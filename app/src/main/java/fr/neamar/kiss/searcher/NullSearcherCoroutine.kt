package fr.neamar.kiss.searcher

import fr.neamar.kiss.MainActivity

/**
 * NullSearcher의 Coroutines 버전
 *
 * 아무런 결과도 표시하지 않는 Searcher입니다.
 * Minimalistic mode에서 home을 다시 눌렀을 때 loader를 표시하지 않기 위해 사용됩니다.
 *
 * Migration Notes:
 * - Empty doInBackground() 유지
 * - displayActivityLoader() override하여 loader 표시 안 함
 * - 가장 간단한 Searcher (테스트용으로도 좋음)
 */
class NullSearcherCoroutine(
    activity: MainActivity
) : SearcherCoroutine(activity, "<null>", false) {

    /**
     * Loader를 표시하지 않습니다.
     * NullSearcher는 결과가 없으므로 loader를 표시할 필요가 없습니다.
     */
    override fun displayActivityLoader() {
        // Don't display the loader for the NullSearcher
        // (otherwise, pressing home again in minimalistic mode displays the loader for no reason)
    }

    /**
     * 백그라운드에서 아무것도 하지 않습니다.
     * 결과가 없으므로 검색 로직이 필요 없습니다.
     */
    override suspend fun doInBackground() {
        // nothing found ;)
    }
}
