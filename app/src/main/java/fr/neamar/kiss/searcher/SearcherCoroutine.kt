package fr.neamar.kiss.searcher

import android.content.Context
import android.util.Log
import com.amplitude.api.Amplitude
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.pojo.Pojo
import fr.neamar.kiss.pojo.RelevanceComparator
import fr.neamar.kiss.result.Result
import kotlinx.coroutines.*
import org.json.JSONException
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.*

/**
 * Kotlin Coroutines replacement for Searcher (ExecutorService → Coroutines)
 * 
 * Phase 1 Goal: Maintain functional equivalence with Searcher.java
 * - Same lifecycle: onPreExecute → doInBackground → onPostExecute
 * - Same memory safety: WeakReference for MainActivity
 * - Same result processing: PriorityQueue with RelevanceComparator
 * - Same error handling: Log and call onCancelled
 * 
 * Phase 2 improvements (later):
 * - Thread-safe collections
 * - Enhanced error handling
 * - Additional cancellation checks
 */
abstract class SearcherCoroutine(
    activity: MainActivity,
    protected val query: String?,
    private val isRefresh: Boolean
) {
    companion object {
        private const val TAG = "SearcherCoroutine"
        const val DEFAULT_MAX_RESULTS = 50
        
        /**
         * Single thread dispatcher to ensure sequential search execution
         * Replaces: Executors.newSingleThreadExecutor()
         */
        private val searchDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
    
    // WeakReference to prevent memory leaks (same as Searcher.java)
    protected val activityWeakReference = WeakReference(activity)
    
    // PriorityQueue for result processing (same as Searcher.java)
    private val processedPojos = PriorityQueue<Pojo>(DEFAULT_MAX_RESULTS, RelevanceComparator())
    
    // Job for cancellation control
    private var currentJob: Job? = null
    
    // Performance tracking
    private var startTime: Long = 0
    
    /**
     * Get custom PriorityQueue processor
     * Can be overridden by subclasses (e.g., ApplicationsSearcher)
     */
    protected open fun getPojoProcessor(context: Context): PriorityQueue<Pojo> {
        return PriorityQueue(DEFAULT_MAX_RESULTS, RelevanceComparator())
    }
    
    /**
     * Get maximum result count
     * Can be overridden by subclasses (e.g., QuerySearcher)
     */
    protected open fun getMaxResultCount(): Int {
        return DEFAULT_MAX_RESULTS
    }
    
    /**
     * Add single pojo to results
     * Called from background thread by providers
     */
    fun addResult(pojo: Pojo): Boolean {
        return addResults(listOf(pojo))
    }
    
    /**
     * Add multiple pojos to results
     * Called from background thread by providers
     * 
     * Thread-safe: Uses synchronized block to ensure safe concurrent access
     * Phase 2 Step 1: Explicit thread safety
     */
    open fun addResults(pojos: List<Pojo>): Boolean {
        if (isCancelled()) {
            return false
        }
        // Synchronize on processedPojos to ensure thread-safe operations
        synchronized(processedPojos) {
            return processedPojos.addAll(pojos)
        }
    }
    
    /**
     * Check if search is cancelled
     */
    fun isCancelled(): Boolean {
        return currentJob?.isCancelled ?: false
    }
    
    /**
     * Cancel the search operation
     */
    fun cancel() {
        currentJob?.cancel()
    }
    
    /**
     * Execute the search operation
     * Returns Job for cancellation control
     */
    fun execute(): Job {
        // Cancel previous job if exists
        currentJob?.cancel()
        
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Phase 1: Maintain same lifecycle as Searcher.java
                onPreExecute()
                
                // Background work on single thread dispatcher
                withContext(searchDispatcher) {
                    doInBackground()
                }
                
                // UI update on main thread
                onPostExecute()
                
            } catch (e: Exception) {
                // Phase 1: Same error handling as Searcher.java
                // All exceptions treated as cancellation
                Log.e(TAG, "Error in searcher", e)
                onCancelled()
            }
        }
        
        return currentJob!!
    }
    
    /**
     * Called on main thread before background work
     * Same as Searcher.java onPreExecute()
     */
    protected open fun onPreExecute() {
        startTime = System.currentTimeMillis()
        displayActivityLoader()
    }
    
    /**
     * Display loading indicator
     */
    protected open fun displayActivityLoader() {
        val activity = activityWeakReference.get() ?: return
        activity.displayLoader(true)
    }
    
    /**
     * Background work - implemented by subclasses
     * Runs on background thread (searchDispatcher)
     */
    protected abstract suspend fun doInBackground()
    
    /**
     * Called on main thread after background work completes
     * Same as Searcher.java onPostExecute()
     */
    protected open fun onPostExecute() {
        if (isCancelled()) {
            return
        }
        
        val activity = activityWeakReference.get() ?: return
        
        hideActivityLoader(activity)
        
        if (processedPojos.isEmpty()) {
            activity.adapter.clear()
        } else {
            // Process results: limit to max count
            val maxResults = getMaxResultCount()
            while (processedPojos.size > maxResults) {
                processedPojos.poll()
            }
            
            // Convert to Result list
            val results = ArrayList<Result<*>>(processedPojos.size)
            while (processedPojos.peek() != null) {
                results.add(Result.fromPojo(activity, processedPojos.poll()))
            }
            
            // Update adapter
            activity.beforeListChange()
            activity.adapter.updateResults(activity, results, isRefresh, query)
            activity.afterListChange()
        }
        
        // Reset task reference
        activity.resetTask()
        
        // Performance logging (same as Searcher.java)
        logPerformance(activity)
    }
    
    /**
     * Hide loading indicator
     */
    private fun hideActivityLoader(activity: MainActivity) {
        // Loader should still be displayed until all providers have loaded
        val dataHandler = KissApplication.getApplication(activity).dataHandler
        activity.displayLoader(!dataHandler.allProvidersHaveLoaded)
    }
    
    /**
     * Log search performance
     * Same as Searcher.java
     */
    private fun logPerformance(activity: MainActivity) {
        val time = System.currentTimeMillis() - startTime
        Log.v(TAG, "Time to run query `$query` on ${this::class.simpleName} to completion: ${time}ms")
        
        try {
            val eventProperties = JSONObject()
            eventProperties.put("type", this::class.simpleName)
            eventProperties.put("length", query?.replace("<null>", "")?.length ?: 0)
            eventProperties.put("time", time)
            
            val dataHandler = KissApplication.getApplication(activity).dataHandler
            eventProperties.put("allProvidersHaveLoaded", dataHandler.allProvidersHaveLoaded)
            
            Amplitude.getInstance().logEvent("Search", eventProperties)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Called when search is cancelled or encounters error
     * Same as Searcher.java onCancelled()
     */
    protected open fun onCancelled() {
        val activity = activityWeakReference.get() ?: return
        hideActivityLoader(activity)
    }
}
