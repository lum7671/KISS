package fr.neamar.kiss.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 데이터 저장소를 위한 기본 인터페이스
 * 
 * @param <T> 저장할 데이터 타입
 * @param <ID> 식별자 타입
 */
public interface Repository<T, ID> {
    
    /**
     * 모든 항목을 조회합니다.
     * 
     * @return 모든 항목의 목록
     */
    @NonNull
    List<T> findAll();
    
    /**
     * ID로 항목을 조회합니다.
     * 
     * @param id 조회할 항목의 ID
     * @return 항목 또는 null
     */
    @Nullable
    T findById(@NonNull ID id);
    
    /**
     * 쿼리로 항목들을 검색합니다.
     * 
     * @param query 검색 쿼리
     * @return 검색 결과 목록
     */
    @NonNull
    List<T> findByQuery(@NonNull String query);
    
    /**
     * 항목을 저장합니다.
     * 
     * @param item 저장할 항목
     * @return 저장된 항목
     */
    @NonNull
    T save(@NonNull T item);
    
    /**
     * 여러 항목을 한번에 저장합니다.
     * 
     * @param items 저장할 항목들
     * @return 저장된 항목들
     */
    @NonNull
    List<T> saveAll(@NonNull List<T> items);
    
    /**
     * ID로 항목을 삭제합니다.
     * 
     * @param id 삭제할 항목의 ID
     * @return 삭제 성공 여부
     */
    boolean deleteById(@NonNull ID id);
    
    /**
     * 항목을 삭제합니다.
     * 
     * @param item 삭제할 항목
     * @return 삭제 성공 여부
     */
    boolean delete(@NonNull T item);
    
    /**
     * 모든 항목을 삭제합니다.
     */
    void deleteAll();
    
    /**
     * 저장소에 저장된 항목 수를 반환합니다.
     * 
     * @return 항목 수
     */
    long count();
    
    /**
     * ID로 항목의 존재 여부를 확인합니다.
     * 
     * @param id 확인할 ID
     * @return 존재 여부
     */
    boolean existsById(@NonNull ID id);
}
