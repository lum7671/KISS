package fr.neamar.kiss.searcher

import fr.neamar.kiss.MainActivity
import fr.neamar.kiss.pojo.PojoWithTags

/**
 * UntaggedSearcher의 Coroutines 버전
 * 
 * 태그가 없는 모든 결과를 반환합니다.
 * Untagged 앱 보기에서 사용됩니다.
 * 
 * Migration Notes:
 * - Extends PojoWithTagSearcherCoroutine
 * - Override acceptPojo() only: Check if pojo has no tags
 * - Very simple implementation (18 lines in original)
 * 
 * Phase 1: 100% functional equivalence (no optimization)
 */
class UntaggedSearcherCoroutine(
    activity: MainActivity
) : PojoWithTagSearcherCoroutine(activity, "<untagged>") {

    /**
     * acceptPojo() implementation
     * 
     * Accept pojo if it has no tags (null or empty).
     * 
     * @param pojoWithTags The pojo to check
     * @return true if pojo has no tags
     */
    override fun acceptPojo(pojoWithTags: PojoWithTags): Boolean {
        return pojoWithTags.tags == null || pojoWithTags.tags.isEmpty()
    }
}
