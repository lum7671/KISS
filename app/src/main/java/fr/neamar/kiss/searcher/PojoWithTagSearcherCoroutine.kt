package fr.neamar.kiss.searcher

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.db.HistoryMode
import fr.neamar.kiss.pojo.Pojo
import fr.neamar.kiss.pojo.PojoWithTags

/**
 * PojoWithTagSearcher의 Coroutines 버전 (Abstract Base Class)
 *
 * 태그를 가진 POJO들을 검색하기 위한 추상 기본 클래스입니다.
 * TagsSearcher와 UntaggedSearcher가 이 클래스를 상속합니다.
 *
 * Migration Notes:
 * - Abstract base class for TagsSearcher, UntaggedSearcher
 * - Filter logic in addResults() (only PojoWithTags + acceptPojo())
 * - HistoryMode-based sorting (applyRelevanceFromHistory)
 * - Optimized tag search (requestRecordsByTag for TagsSearcher)
 * - getMaxResultCount() = Integer.MAX_VALUE
 *
 * Phase 1: 100% functional equivalence (no optimization)
 */
abstract class PojoWithTagSearcherCoroutine(
    activity: MainActivity,
    query: String
) : SearcherCoroutine(activity, query, false) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

    /**
     * getMaxResultCount() override
     *
     * Return Integer.MAX_VALUE to show all matching results.
     */
    override fun getMaxResultCount(): Int {
        return Integer.MAX_VALUE
    }

    /**
     * doInBackground() - Main search logic
     *
     * For TagsSearcher with specific tag query: Use optimized requestRecordsByTag()
     * For other cases (UntaggedSearcher, generic TagsSearcher): Use requestAllRecords()
     *
     * Phase 2 Step 3: Added cancellation checks for fast cancellation response
     */
    override suspend fun doInBackground() {
        val activity = activityWeakReference.get() ?: return

        // Phase 2 Step 3: Check cancellation at start
        if (isCancelled()) return

        val dataHandler = KissApplication.getApplication(activity).dataHandler

        // Phase 2 Step 3: Check cancellation before adapter creation
        if (isCancelled()) return

        // Create adapter to bridge SearcherCoroutine → Searcher interface
        // Same pattern as QuerySearcherCoroutine
        val searcherAdapter = object : fr.neamar.kiss.searcher.Searcher(activity, query, false) {
            override fun doInBackground() {
                // Not used - only need addResults() bridge
            }

            // addResult() is final in Searcher, so we override addResults() which it calls
            override fun addResults(pojos: List<fr.neamar.kiss.pojo.Pojo>): Boolean {
                return this@PojoWithTagSearcherCoroutine.addResults(pojos)
            }

            override fun isCancelled(): Boolean {
                return this@PojoWithTagSearcherCoroutine.isCancelled()
            }
        }

        // Phase 2 Step 3: Check cancellation before DataHandler request
        if (isCancelled()) return

        // 태그 검색인 경우 최적화된 메서드 사용
        if (this is TagsSearcherCoroutine && query != null && query != "<tags>") {
            // Optimized: request only records with specific tag
            dataHandler.requestRecordsByTag(query, searcherAdapter)
        } else {
            // General: request all records (filtering in addResults())
            dataHandler.requestAllRecords(searcherAdapter)
        }
    }

    /**
     * addResults() override with filtering
     *
     * 1. Filter: only PojoWithTags that pass acceptPojo() check
     * 2. Apply history-based relevance sorting
     * 3. Call super.addResults()
     *
     * Phase 2 Step 3: Added cancellation checks for fast cancellation response
     */
    override fun addResults(pojos: List<Pojo>): Boolean {
        val activity = activityWeakReference.get() ?: return false

        // Phase 2 Step 3: Check cancellation at start
        if (isCancelled()) return false

        // Filter: only PojoWithTags + acceptPojo()
        val filteredPojos = ArrayList<Pojo>()
        for (pojo in pojos) {
            // Phase 2 Step 3: Check cancellation in loop
            if (isCancelled()) return false

            if (pojo !is PojoWithTags) {
                continue
            }
            if (acceptPojo(pojo)) {
                filteredPojos.add(pojo)
            }
        }

        // Phase 2 Step 3: Check cancellation before DataHandler query
        if (isCancelled()) return false

        // Apply history-based relevance
        val dataHandler = KissApplication.getApplication(activity).dataHandler
        dataHandler.applyRelevanceFromHistory(filteredPojos, getTaggedResultSortMode())

        return super.addResults(filteredPojos)
    }

    /**
     * Get HistoryMode for tagged result sorting
     *
     * Reads "tagged-result-sort-mode" preference.
     * - "default": Use DataHandler's historyMode
     * - Other values: Use specific HistoryMode
     */
    private fun getTaggedResultSortMode(): HistoryMode {
        val sortMode = prefs.getString("tagged-result-sort-mode", "default")
        if (sortMode == "default") {
            val activity = activityWeakReference.get() ?: return HistoryMode.FRECENCY
            return KissApplication.getApplication(activity).dataHandler.historyMode
        }
        return HistoryMode.valueById(sortMode)
    }

    /**
     * Abstract method: acceptPojo()
     *
     * Subclasses implement this to define filtering logic.
     * - TagsSearcher: Check if pojo has specific tag
     * - UntaggedSearcher: Check if pojo has no tags
     *
     * @param pojoWithTags The pojo to check
     * @return true if pojo should be included in results
     */
    protected abstract fun acceptPojo(pojoWithTags: PojoWithTags): Boolean
}
