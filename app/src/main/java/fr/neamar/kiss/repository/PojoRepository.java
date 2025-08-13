package fr.neamar.kiss.repository;

import androidx.annotation.NonNull;

import java.util.List;

import fr.neamar.kiss.pojo.Pojo;

/**
 * Pojo 전용 Repository 인터페이스
 */
public interface PojoRepository<T extends Pojo> extends Repository<T, String> {
    
    /**
     * 태그로 항목들을 검색합니다.
     * 
     * @param tag 검색할 태그
     * @return 해당 태그를 가진 항목들
     */
    @NonNull
    List<T> findByTag(@NonNull String tag);
    
    /**
     * 관련성 점수 범위로 항목들을 검색합니다.
     * 
     * @param minRelevance 최소 관련성 점수
     * @param maxRelevance 최대 관련성 점수
     * @return 해당 범위의 관련성을 가진 항목들
     */
    @NonNull
    List<T> findByRelevanceRange(int minRelevance, int maxRelevance);
    
    /**
     * 관련성 점수 순으로 정렬된 전체 목록을 반환합니다.
     * 
     * @return 관련성 순으로 정렬된 항목들
     */
    @NonNull
    List<T> findAllOrderByRelevance();
    
    /**
     * 이름 부분 일치로 항목들을 검색합니다.
     * 
     * @param namePart 검색할 이름 부분
     * @return 이름에 해당 부분을 포함하는 항목들
     */
    @NonNull
    List<T> findByNameContaining(@NonNull String namePart);
    
    /**
     * 캐시를 무효화합니다.
     */
    void invalidateCache();
    
    /**
     * 캐시를 새로고침합니다.
     */
    void refreshCache();
}
