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

    // Store user preferences (same as QuerySearcher.java)
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

    // Known IDs from query history (same as QuerySearcher.java)
    private lateinit var knownIds: HashMap<String, Int>

    /**
     * Phase 2 Step 4: Changed from static to instance variable
     * - Removed static MAX_RESULT_COUNT and clearMaxResultCountCache()
     * - Each searcher instance has its own maxResultCount
     * - No need for manual cache invalidation
     */
    private var maxResultCount: Int? = null

    /**
     * Get maximum result count from preferences
     * Phase 2 Step 4: Instance-based caching instead of static
     */
    override fun getMaxResultCount(): Int {
        if (maxResultCount == null) {
            // Convert to double first before truncating to int to avoid
            // java.lang.NumberFormatException crashes for values larger than Integer.MAX_VALUE
            try {
                maxResultCount = prefs.getString(
                    "number-of-display-elements",
                    DEFAULT_MAX_RESULTS.toString()
                )?.toDouble()?.toInt() ?: DEFAULT_MAX_RESULTS
            } catch (e: NumberFormatException) {
                // If, for any reason, setting is empty, return default value
                maxResultCount = DEFAULT_MAX_RESULTS
            }
        }

        return maxResultCount!!
    }

    /**
     * Add results with relevance adjustments
     * Same logic as QuerySearcher.java:
     * - Penalty for disabled items (-200)
     * - Boost for previously selected items (+25 * selection count)
     */
    override fun addResults(pojos: List<Pojo>): Boolean {
        val activity = activityWeakReference.get() ?: return false

        for (pojo in pojos) {
            if (pojo.isDisabled) {
                val recentUsageCount = DBHelper.getUsageCountForRecord(activity, pojo.id, 30)
                if (recentUsageCount >= 1) {
                    // Frequently used despite being disabled: no penalty, still apply history boost
                    val value = knownIds[pojo.id]
                    if (value != null) {
                        pojo.relevance += 25 * value
                    }
                } else {
                    // Infrequently used disabled app: keep penalty
                    pojo.relevance -= 200
                }
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
