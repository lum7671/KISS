package fr.neamar.kiss.searcher

import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.pojo.PojoWithTags

/**
 * TagsSearcher의 Coroutines 버전
 * 
 * 특정 태그를 가진 모든 결과를 반환합니다.
 * 태그 검색 메뉴에서 사용됩니다.
 * 
 * Migration Notes:
 * - Extends PojoWithTagSearcherCoroutine
 * - Override acceptPojo() only: Check if pojo has specific tag
 * - Very simple implementation (19 lines in original)
 * 
 * Phase 1: 100% functional equivalence (no optimization)
 */
class TagsSearcherCoroutine(
    activity: MainActivity,
    query: String?
) : PojoWithTagSearcherCoroutine(activity, query ?: "<tags>") {

    /**
     * acceptPojo() implementation
     * 
     * Accept pojo if it has tags and contains the query tag.
     * 
     * @param pojoWithTags The pojo to check
     * @return true if pojo has the specified tag
     */
    override fun acceptPojo(pojoWithTags: PojoWithTags): Boolean {
        val tags = pojoWithTags.tags ?: return false
        return tags.contains(query as String)
    }
}
