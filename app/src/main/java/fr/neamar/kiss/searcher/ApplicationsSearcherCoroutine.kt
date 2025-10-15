package fr.neamar.kiss.searcher

import android.content.Context
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.pojo.AppPojo
import fr.neamar.kiss.pojo.Pojo
import fr.neamar.kiss.pojo.ReversedNameComparator
import fr.neamar.kiss.pojo.ShortcutPojo
import java.util.PriorityQueue

/**
 * ApplicationsSearcher의 Coroutines 버전
 * 
 * 시스템의 모든 앱 목록을 반환합니다.
 * App drawer를 표시할 때 사용됩니다.
 * 
 * Migration Notes:
 * - Custom PriorityQueue: ReversedNameComparator (A→Z sorting, reversed for ListView)
 * - getMaxResultCount() = Integer.MAX_VALUE (모든 앱 표시)
 * - Filter favorites logic 보존
 * - onPostExecuteInternal() override: adapter.buildSections() 호출
 * 
 * Phase 1: 100% functional equivalence (no optimization)
 */
class ApplicationsSearcherCoroutine(
    activity: MainActivity,
    isRefresh: Boolean
) : SearcherCoroutine(activity, "<application>", isRefresh) {

    /**
     * getPojoProcessor() override
     * 
     * Custom PriorityQueue with ReversedNameComparator.
     * Sort from A to Z, so reverse (last item needs to be A, ListView starts at the bottom).
     */
    override fun getPojoProcessor(context: Context): PriorityQueue<Pojo> {
        return PriorityQueue(DEFAULT_MAX_RESULTS, ReversedNameComparator())
    }

    /**
     * getMaxResultCount() override
     * 
     * Return Integer.MAX_VALUE to show all apps.
     */
    override fun getMaxResultCount(): Int {
        return Integer.MAX_VALUE
    }

    /**
     * doInBackground() - Main search logic
     * 
     * 1. Get excluded favorites from DataHandler
     * 2. Add all apps (without excluded favorites)
     * 3. Add pinned shortcuts (PWA, ...)
     * 
     * Phase 2 Step 3: Added cancellation checks for fast cancellation response
     */
    override suspend fun doInBackground() {
        val activity = activityWeakReference.get() ?: return

        // Phase 2 Step 3: Check cancellation at start
        if (isCancelled()) return

        val dataHandler = KissApplication.getApplication(activity).dataHandler
        val excludedFavoriteIds = dataHandler.excludedFavorites

        // Phase 2 Step 3: Check cancellation before DataHandler query
        if (isCancelled()) return

        // Add apps
        val pojos = dataHandler.applicationsWithoutExcluded
        if (pojos != null) {
            // Phase 2 Step 3: Check cancellation before processing
            if (isCancelled()) return
            addResults(getPojosWithoutFavorites(pojos, excludedFavoriteIds))
        }

        // Phase 2 Step 3: Check cancellation before shortcuts
        if (isCancelled()) return

        // Add pinned shortcuts (PWA, ...)
        val shortcuts = dataHandler.pinnedShortcuts
        if (shortcuts != null) {
            // Phase 2 Step 3: Check cancellation before processing
            if (isCancelled()) return
            addResults(getPojosWithoutFavorites(shortcuts, excludedFavoriteIds))
        }
    }

    /**
     * onPostExecute() override
     * 
     * Build sections for fast scrolling after results are displayed.
     */
    override fun onPostExecute() {
        super.onPostExecute()

        val activity = activityWeakReference.get() ?: return

        // Build sections for fast scrolling
        activity.adapter.buildSections()
    }

    /**
     * Filter favorites from pojos
     * 
     * @param pojos List of pojos
     * @param excludedFavoriteIds IDs of favorites to exclude from pojos
     * @return Pojos without favorites
     */
    private fun <T : Pojo> getPojosWithoutFavorites(
        pojos: List<T>,
        excludedFavoriteIds: Set<String>
    ): List<T> {
        if (excludedFavoriteIds.isEmpty()) {
            return pojos
        }

        val records = ArrayList<T>(pojos.size)
        for (pojo in pojos) {
            if (!excludedFavoriteIds.contains(pojo.favoriteId)) {
                records.add(pojo)
            }
        }
        return records
    }
}
