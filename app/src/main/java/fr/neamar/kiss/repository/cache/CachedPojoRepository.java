package fr.neamar.kiss.repository.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import fr.neamar.kiss.normalizer.StringNormalizer;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.repository.PojoRepository;

/**
 * 메모리 캐시를 사용하는 Pojo Repository 구현체
 */
public class CachedPojoRepository<T extends Pojo> implements PojoRepository<T> {
    
    private final Map<String, T> cache = new ConcurrentHashMap<>();
    private final Map<String, List<T>> tagCache = new ConcurrentHashMap<>();
    private final List<T> allItems = new CopyOnWriteArrayList<>();
    
    private volatile boolean cacheValid = false;
    
    @NonNull
    @Override
    public List<T> findAll() {
        return new ArrayList<>(allItems);
    }
    
    @Nullable
    @Override
    public T findById(@NonNull String id) {
        return cache.get(id);
    }
    
    @NonNull
    @Override
    public List<T> findByQuery(@NonNull String query) {
        if (query.trim().isEmpty()) {
            return findAll();
        }
        
        String normalizedQuery = query.toLowerCase().trim();
        List<T> results = new ArrayList<>();
        
        for (T item : allItems) {
            if (matchesQuery(item, normalizedQuery)) {
                results.add(item);
            }
        }
        
        // 관련성 점수로 정렬
        results.sort((a, b) -> Integer.compare(b.relevance, a.relevance));
        return results;
    }
    
    private boolean matchesQuery(@NonNull T item, @NonNull String normalizedQuery) {
        // 이름 매칭
        if (item.getName().toLowerCase().contains(normalizedQuery)) {
            return true;
        }
        
        // 정규화된 이름 매칭
        if (item.normalizedName != null && 
            item.normalizedName.codePoints != null) {
            String normalizedName = item.normalizedName.toString();
            if (normalizedName.contains(normalizedQuery)) {
                return true;
            }
        }
        
        return false;
    }
    
    @NonNull
    @Override
    public T save(@NonNull T item) {
        cache.put(item.id, item);
        
        // allItems에서 기존 항목 제거 후 새 항목 추가
        allItems.removeIf(existing -> existing.id.equals(item.id));
        allItems.add(item);
        
        invalidateTagCache();
        return item;
    }
    
    @NonNull
    @Override
    public List<T> saveAll(@NonNull List<T> items) {
        List<T> savedItems = new ArrayList<>();
        for (T item : items) {
            savedItems.add(save(item));
        }
        return savedItems;
    }
    
    @Override
    public boolean deleteById(@NonNull String id) {
        T removed = cache.remove(id);
        if (removed != null) {
            allItems.removeIf(item -> item.id.equals(id));
            invalidateTagCache();
            return true;
        }
        return false;
    }
    
    @Override
    public boolean delete(@NonNull T item) {
        return deleteById(item.id);
    }
    
    @Override
    public void deleteAll() {
        cache.clear();
        allItems.clear();
        tagCache.clear();
    }
    
    @Override
    public long count() {
        return allItems.size();
    }
    
    @Override
    public boolean existsById(@NonNull String id) {
        return cache.containsKey(id);
    }
    
    @NonNull
    @Override
    public List<T> findByTag(@NonNull String tag) {
        String normalizedTag = tag.toLowerCase().trim();
        
        // 캐시에서 먼저 확인
        List<T> cached = tagCache.get(normalizedTag);
        if (cached != null) {
            return new ArrayList<>(cached);
        }
        
        // 캐시에 없으면 검색 후 캐싱
        List<T> results = allItems.stream()
            .filter(item -> hasTag(item, normalizedTag))
            .collect(Collectors.toList());
        
        tagCache.put(normalizedTag, new ArrayList<>(results));
        return results;
    }
    
    private boolean hasTag(@NonNull T item, @NonNull String tag) {
        // 기본 구현: 이름에서 태그 검색
        // 실제 구현에서는 PojoWithTags의 getTags() 메서드 사용
        return item.getName().toLowerCase().contains(tag);
    }
    
    @NonNull
    @Override
    public List<T> findByRelevanceRange(int minRelevance, int maxRelevance) {
        return allItems.stream()
            .filter(item -> item.relevance >= minRelevance && item.relevance <= maxRelevance)
            .collect(Collectors.toList());
    }
    
    @NonNull
    @Override
    public List<T> findAllOrderByRelevance() {
        List<T> sorted = new ArrayList<>(allItems);
        sorted.sort((a, b) -> Integer.compare(b.relevance, a.relevance));
        return sorted;
    }
    
    @NonNull
    @Override
    public List<T> findByNameContaining(@NonNull String namePart) {
        String normalizedPart = namePart.toLowerCase().trim();
        return allItems.stream()
            .filter(item -> item.getName().toLowerCase().contains(normalizedPart))
            .collect(Collectors.toList());
    }
    
    @Override
    public void invalidateCache() {
        tagCache.clear();
        cacheValid = false;
    }
    
    private void invalidateTagCache() {
        tagCache.clear();
    }
    
    @Override
    public void refreshCache() {
        invalidateCache();
        cacheValid = true;
    }
    
    /**
     * 캐시 통계 정보를 반환합니다.
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            cache.size(),
            tagCache.size(),
            allItems.size(),
            cacheValid
        );
    }
    
    /**
     * 캐시 통계 정보 클래스
     */
    public static class CacheStats {
        public final int itemCacheSize;
        public final int tagCacheSize;
        public final int totalItems;
        public final boolean isValid;
        
        CacheStats(int itemCacheSize, int tagCacheSize, int totalItems, boolean isValid) {
            this.itemCacheSize = itemCacheSize;
            this.tagCacheSize = tagCacheSize;
            this.totalItems = totalItems;
            this.isValid = isValid;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{items=%d, tags=%d, total=%d, valid=%s}", 
                itemCacheSize, tagCacheSize, totalItems, isValid);
        }
    }
}
