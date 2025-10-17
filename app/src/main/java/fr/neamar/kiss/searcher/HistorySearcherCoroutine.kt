package fr.neamar.kiss.searcher

import android.content.SharedPreferences
import android.os.Build
import androidx.preference.PreferenceManager
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.db.HistoryMode
import fr.neamar.kiss.pojo.AppPojo
import fr.neamar.kiss.pojo.Pojo
import fr.neamar.kiss.utils.ShortcutUtil

/**
 * HistorySearcher의 Coroutines 버전
 *
 * 히스토리에서 POJO들을 검색합니다.
 * 빈 검색어일 때 호출되어 사용자의 앱 사용 히스토리를 표시합니다.
 *
 * Migration Notes:
 * - SharedPreferences로 getMaxResultCount() 읽기 (static cache 사용)
 * - Exclude favorites/history 로직 보존
 * - Shortcut handling (API 26+) 보존
 * - Disabled items penalty (-200) 보존
 * - QuerySearcher와 유사한 패턴
 *
 * Phase 1: 100% functional equivalence (no optimization)
 */
class HistorySearcherCoroutine(
    activity: MainActivity,
    isRefresh: Boolean
) : SearcherCoroutine(activity, "<history>", isRefresh) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity)

    /**
     * Phase 2 Step 4: Changed from static to instance variable
     * - Removed static maxResultCountCache and clearMaxResultCountCache()
     * - Each searcher instance has its own maxResultCount
     * - No need for manual cache invalidation
     */
    private var maxResultCount: Int? = null

    /**
     * getMaxResultCount() override
     *
     * Reads "number-of-display-elements" preference.
     * Converts to double first to avoid NumberFormatException for values > Integer.MAX_VALUE.
     *
     * Phase 2 Step 4: Instance-based caching instead of static
     */
    override fun getMaxResultCount(): Int {
        if (maxResultCount == null) {
            // Convert to double first before truncating to int to avoid
            // java.lang.NumberFormatException crashes for values larger than Integer.MAX_VALUE
            maxResultCount = try {
                prefs.getString(
                    "number-of-display-elements",
                    DEFAULT_MAX_RESULTS.toString()
                )?.toDoubleOrNull()?.toInt() ?: DEFAULT_MAX_RESULTS
            } catch (e: NumberFormatException) {
                DEFAULT_MAX_RESULTS
            }
        }
        return maxResultCount!!
    }

    /**
     * doInBackground() - Main search logic
     *
     * 1. Gather excluded items (from history, favorites)
     * 2. Add shortcuts for excluded apps (API 26+)
     * 3. Get history from DataHandler
     * 4. Add results
     *
     * Phase 2 Step 3: Added cancellation checks for fast cancellation response
     */
    override suspend fun doInBackground() {
        val activity = activityWeakReference.get() ?: return

        // Phase 2 Step 3: Check cancellation at start
        if (isCancelled()) return

        // Read preferences
        val excludeFavorites = prefs.getBoolean("exclude-favorites-history", false)

        val dataHandler = KissApplication.getApplication(activity).dataHandler

        // Gather excluded items
        val excludedFromHistory = dataHandler.excludedFromHistory
        val excludedPojoById = HashSet(excludedFromHistory)

        // Phase 2 Step 3: Check cancellation before expensive shortcut processing
        if (isCancelled()) return

        // Add ids of shortcuts for excluded apps (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            for (id in excludedFromHistory) {
                // Phase 2 Step 3: Check cancellation in loop
                if (isCancelled()) return

                val pojo = dataHandler.getItemById(id)
                if (pojo is AppPojo) {
                    val shortcutInfos = ShortcutUtil.getShortcuts(activity, pojo.packageName)
                    for (shortcutInfo in shortcutInfos) {
                        val shortcutRecord = ShortcutUtil.createShortcutRecord(
                            activity,
                            shortcutInfo,
                            !shortcutInfo.isPinned
                        )
                        if (shortcutRecord != null) {
                            excludedPojoById.add(ShortcutUtil.generateShortcutId(shortcutRecord))
                        }
                    }
                }
            }
        }

        // Gather favorites to exclude
        if (excludeFavorites) {
            for (favoritePojo in dataHandler.favorites) {
                // Phase 2 Step 3: Check cancellation in loop
                if (isCancelled()) return
                excludedPojoById.add(favoritePojo.id)
            }
        }

        // Phase 2 Step 3: Check cancellation before DataHandler query
        if (isCancelled()) return

        // Get history from DataHandler
        val pojos = dataHandler.getHistory(activity, getMaxResultCount(), excludedPojoById)

        // Phase 2 Step 3: Check cancellation before adding results
        if (isCancelled()) return

        // Add results
        addResults(pojos)
    }

    /**
     * addResults() override
     *
     * Apply relevance penalty for disabled items when not in ALPHABETICALLY mode.
     * Disabled items should not be preferred in history.
     */
    override fun addResults(pojos: List<Pojo>): Boolean {
        val activity = activityWeakReference.get() ?: return false

        val dataHandler = KissApplication.getApplication(activity).dataHandler

        // Apply penalty for disabled items (not in ALPHABETICALLY mode)
        if (dataHandler.historyMode != HistoryMode.ALPHABETICALLY) {
            for (pojo in pojos) {
                if (pojo.isDisabled) {
                    // Give penalty for disabled items, these should not be preferred
                    pojo.relevance -= 200
                }
            }
        }

        return super.addResults(pojos)
    }
}
