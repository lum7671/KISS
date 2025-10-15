package fr.neamar.kiss.searcher

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.db.DBHelper
import fr.neamar.kiss.pojo.Pojo

/**
 * Kotlin Coroutines replacement for QuerySearcher
 * 
 * Phase 1 Goal: Maintain functional equivalence with QuerySearcher.java
 * - Same query execution logic
 * - Same result processing (relevance boosting)
 * - Same history-based ranking
 * - Same MAX_RESULT_COUNT caching
 * 
 * This is the most critical searcher - handles all user search queries.
 */
class QuerySearcherCoroutine(
    activity: MainActivity,
    query: String,
    isRefresh: Boolean
) : SearcherCoroutine(activity, query, isRefresh) {
    
    companion object {
        /**
         * Static cache for MAX_RESULT_COUNT
         * Phase 2 improvement: Remove static mutable state
         */
        @Volatile
        private var MAX_RESULT_COUNT = -1
        
        /**
         * Clear the cached max result count
         * Called when user changes preference
         */
        @JvmStatic
        fun clearMaxResultCountCache() {
            MAX_RESULT_COUNT = -1
        }
    }
    
    // Store user preferences (same as QuerySearcher.java)
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)
    
    // Known IDs from query history (same as QuerySearcher.java)
    private lateinit var knownIds: HashMap<String, Int>
    
    /**
     * Get maximum result count from preferences
     * Same as QuerySearcher.java with static caching
     */
    override fun getMaxResultCount(): Int {
        if (MAX_RESULT_COUNT == -1) {
            // Convert to double first before truncating to int to avoid
            // java.lang.NumberFormatException crashes for values larger than Integer.MAX_VALUE
            try {
                MAX_RESULT_COUNT = prefs.getString(
                    "number-of-display-elements",
                    DEFAULT_MAX_RESULTS.toString()
                )?.toDouble()?.toInt() ?: DEFAULT_MAX_RESULTS
            } catch (e: NumberFormatException) {
                // If, for any reason, setting is empty, return default value
                MAX_RESULT_COUNT = DEFAULT_MAX_RESULTS
            }
        }
        
        return MAX_RESULT_COUNT
    }
    
    /**
     * Add results with relevance adjustments
     * Same logic as QuerySearcher.java:
     * - Penalty for disabled items (-200)
     * - Boost for previously selected items (+25 * selection count)
     */
    override fun addResults(pojos: List<Pojo>): Boolean {
        for (pojo in pojos) {
            if (pojo.isDisabled) {
                // Give penalty for disabled items, these should not be preferred
                pojo.relevance -= 200
            } else {
                // Give a boost if item was previously selected for this query
                val value = knownIds[pojo.id]
                if (value != null) {
                    pojo.relevance += 25 * value
                }
            }
        }
        
        // Call super implementation to update the adapter
        return super.addResults(pojos)
    }
    
    /**
     * Background work - called on background thread (searchDispatcher)
     * Same logic as QuerySearcher.java:
     * 1. Load query history from DB
     * 2. Request results from DataHandler
     * 
     * Phase 2 Step 3: Added cancellation checks for fast cancellation response
     */
    override suspend fun doInBackground() {
        val activity = activityWeakReference.get() ?: return
        
        // Phase 2 Step 3: Check cancellation before DB query
        if (isCancelled()) return
        
        // Have we ever made the same query and selected something?
        val lastIdsForQuery = DBHelper.getPreviousResultsForQuery(activity, query)
        
        // Phase 2 Step 3: Check cancellation before HashMap creation
        if (isCancelled()) return
        
        knownIds = HashMap()
        for (id in lastIdsForQuery) {
            // Phase 2 Step 3: Check cancellation in loop (for large result sets)
            if (isCancelled()) return
            knownIds[id.record] = id.value
        }
        
        // Phase 2 Step 3: Check cancellation before Provider request
        if (isCancelled()) return
        
        // Request results via "addResult"
        // Create adapter to bridge SearcherCoroutine → Searcher interface
        // Phase 2 improvement: Refactor Provider interface to accept common interface
        val searcherAdapter = object : Searcher(activity, query, false) {
            override fun doInBackground() {
                // Not used - only need addResult() bridge
            }
            
            // addResult() is final in Searcher, so we override addResults() which it calls
            override fun addResults(pojos: List<Pojo>): Boolean {
                return this@QuerySearcherCoroutine.addResults(pojos)
            }
            
            override fun isCancelled(): Boolean {
                return this@QuerySearcherCoroutine.isCancelled()
            }
        }
        
        KissApplication.getApplication(activity).dataHandler.requestResults(query, searcherAdapter)
    }
}
