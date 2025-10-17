package fr.neamar.kiss.utils

import android.util.Log
import com.amplitude.api.Amplitude
import org.json.JSONException
import org.json.JSONObject

/**
 * Centralized logging for Searcher performance and errors
 *
 * Phase 2 Step 5: Consolidate search logging
 * Provides consistent logging format across all searchers
 * with both Android Log and Amplitude tracking.
 *
 * Benefits:
 * - Consistent log format across all searchers
 * - Single source of truth for search metrics
 * - Easier to modify logging strategy
 * - Better separation of concerns
 */
object SearchPerformanceLogger {

    private const val TAG = "SearchPerf"

    /**
     * Search metrics for logging
     *
     * @param searcherType Simple class name of the searcher (e.g., "QuerySearcherCoroutine")
     * @param query Search query string (null or special queries like "<history>")
     * @param timeMs Execution time in milliseconds
     * @param resultCount Number of results returned
     * @param allProvidersLoaded Whether all data providers have finished loading
     * @param cancelled Whether the search was cancelled
     * @param error Exception if an error occurred, null otherwise
     */
    data class SearchMetrics(
        val searcherType: String,
        val query: String?,
        val timeMs: Long,
        val resultCount: Int,
        val allProvidersLoaded: Boolean,
        val cancelled: Boolean = false,
        val error: Exception? = null
    )

    /**
     * Log search completion, cancellation, or error
     *
     * Logs to both Android Log (for debugging) and Amplitude (for analytics).
     *
     * Status determination:
     * - ERROR: error is not null
     * - CANCELLED: cancelled is true
     * - COMPLETED: normal completion
     */
    fun log(metrics: SearchMetrics) {
        // Determine status
        val status = when {
            metrics.error != null -> "ERROR"
            metrics.cancelled -> "CANCELLED"
            else -> "COMPLETED"
        }

        // Android Log
        logToAndroid(metrics, status)

        // Amplitude logging
        logToAmplitude(metrics, status)
    }

    /**
     * Log to Android Log system
     *
     * Format: [STATUS] SearcherType query='...' time=123ms results=45 providersLoaded=true
     *
     * Uses ERROR level for errors, VERBOSE for normal operations.
     */
    private fun logToAndroid(metrics: SearchMetrics, status: String) {
        val logLevel = if (metrics.error != null) Log.ERROR else Log.VERBOSE

        val message = buildString {
            append("[$status] ")
            append("${metrics.searcherType} ")
            append("query='${metrics.query?.replace("<null>", "") ?: ""}' ")
            append("time=${metrics.timeMs}ms ")
            append("results=${metrics.resultCount} ")
            append("providersLoaded=${metrics.allProvidersLoaded}")

            if (metrics.error != null) {
                append(" error=${metrics.error.javaClass.simpleName}: ${metrics.error.message}")
            }
        }

        Log.println(logLevel, TAG, message)
    }

    /**
     * Log to Amplitude analytics
     *
     * Events:
     * - "Search" for normal completions and cancellations
     * - "SearchError" for errors
     *
     * Properties:
     * - type: searcher type
     * - length: query length (0 for special queries)
     * - time: execution time in ms
     * - resultCount: number of results
     * - allProvidersLoaded: whether all providers loaded
     * - status: COMPLETED/CANCELLED/ERROR
     * - errorType: (errors only) exception class name
     * - errorMessage: (errors only) exception message
     */
    private fun logToAmplitude(metrics: SearchMetrics, status: String) {
        try {
            val eventName = if (metrics.error != null) "SearchError" else "Search"

            val eventProperties = JSONObject().apply {
                put("type", metrics.searcherType)
                put("length", metrics.query?.replace("<null>", "")?.length ?: 0)
                put("time", metrics.timeMs)
                put("resultCount", metrics.resultCount)
                put("allProvidersLoaded", metrics.allProvidersLoaded)
                put("status", status)

                if (metrics.error != null) {
                    put("errorType", metrics.error::class.simpleName ?: "Unknown")
                    put("errorMessage", metrics.error.message ?: "")
                }
            }

            Amplitude.getInstance().logEvent(eventName, eventProperties)
        } catch (e: JSONException) {
            Log.e(TAG, "Failed to log to Amplitude", e)
        }
    }
}
