package fr.neamar.kiss.utils;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 고성능 아이콘 캐시 매니저
 * Glide + LruCache + 3단계 캐싱 전략 사용
 */
public class IconCacheManager {
    private static final String TAG = "IconCacheManager";
    
    // 캐시 크기 설정 (메모리 기반)
    private static final int MEMORY_CACHE_SIZE = (int) (Runtime.getRuntime().maxMemory() / 8); // 메모리의 1/8
    private static final int FREQUENT_CACHE_SIZE = 50;  // 자주 사용되는 아이콘
    private static final int RECENT_CACHE_SIZE = 200;   // 최근 사용된 아이콘
    
    // 3단계 캐시 시스템
    private final LruCache<String, Drawable> frequentCache;     // 1단계: 자주 사용 (작지만 빠름)
    private final LruCache<String, Drawable> recentCache;       // 2단계: 최근 사용 (중간 크기)
    private final LruCache<String, Drawable> memoryCache;       // 3단계: 전체 메모리 (큰 크기)
    
    // 사용 빈도 추적
    private final ConcurrentHashMap<String, AtomicInteger> usageCount = new ConcurrentHashMap<>();
    
    // Glide 인스턴스
    private final RequestManager glide;
    private final RequestOptions iconRequestOptions;
    
    // 싱글톤
    private static volatile IconCacheManager instance;
    
    private IconCacheManager(Context context) {
        // Glide 설정
        glide = Glide.with(context.getApplicationContext());
        iconRequestOptions = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)  // 디스크 캐시 활성화
                .skipMemoryCache(false)                    // Glide 메모리 캐시 활성화
                .centerCrop();
        
        // 1단계: 빈번한 사용 아이콘 (가장 빠름)
        frequentCache = new LruCache<String, Drawable>(FREQUENT_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Drawable value) {
                return getDrawableSize(value);
            }
            
            @Override
            protected void entryRemoved(boolean evicted, String key, Drawable oldValue, Drawable newValue) {
                if (evicted) {
                    Log.v(TAG, "Frequent cache evicted: " + key);
                }
            }
        };
        
        // 2단계: 최근 사용 아이콘
        recentCache = new LruCache<String, Drawable>(RECENT_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Drawable value) {
                return getDrawableSize(value);
            }
        };
        
        // 3단계: 전체 메모리 캐시
        memoryCache = new LruCache<String, Drawable>(MEMORY_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Drawable value) {
                return getDrawableSize(value);
            }
            
            @Override
            protected void entryRemoved(boolean evicted, String key, Drawable oldValue, Drawable newValue) {
                if (evicted) {
                    // 메모리 캐시에서 제거되면 사용 빈도도 감소
                    AtomicInteger count = usageCount.get(key);
                    if (count != null && count.get() > 0) {
                        count.decrementAndGet();
                    }
                }
            }
        };
    }
    
    public static IconCacheManager getInstance(Context context) {
        if (instance == null) {
            synchronized (IconCacheManager.class) {
                if (instance == null) {
                    instance = new IconCacheManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 아이콘 로드 (3단계 캐시 + Glide)
     */
    @Nullable
    public Drawable getIcon(String key) {
        // 1단계: 빈번한 사용 캐시 확인 (가장 빠름)
        Drawable icon = frequentCache.get(key);
        if (icon != null) {
            incrementUsage(key);
            return icon;
        }
        
        // 2단계: 최근 사용 캐시 확인
        icon = recentCache.get(key);
        if (icon != null) {
            // 빈번한 사용으로 승격 가능성 확인
            promoteToFrequentIfNeeded(key, icon);
            incrementUsage(key);
            return icon;
        }
        
        // 3단계: 전체 메모리 캐시 확인
        icon = memoryCache.get(key);
        if (icon != null) {
            // 최근 사용 캐시로 승격
            recentCache.put(key, icon);
            incrementUsage(key);
            return icon;
        }
        
        return null; // 캐시 미스
    }
    
    /**
     * 아이콘 저장 (스마트 캐싱)
     */
    public void putIcon(String key, Drawable icon) {
        if (icon == null) return;
        
        // 사용 빈도에 따라 적절한 캐시에 저장
        int usage = getUsageCount(key);
        
        if (usage >= 10) {
            // 빈번한 사용 아이콘
            frequentCache.put(key, icon);
        } else if (usage >= 3) {
            // 최근 사용 아이콘
            recentCache.put(key, icon);
        } else {
            // 일반 메모리 캐시
            memoryCache.put(key, icon);
        }
        
        incrementUsage(key);
    }
    
    /**
     * Glide를 통한 비동기 아이콘 로딩
     */
    public void loadIconAsync(String key, Object source, IconLoadCallback callback) {
        glide.asDrawable()
                .load(source)
                .apply(iconRequestOptions)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        // 로드 완료 후 캐시에 저장
                        putIcon(key, resource);
                        callback.onIconLoaded(key, resource);
                    }
                    
                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        callback.onIconLoadFailed(key);
                    }
                });
    }
    
    /**
     * 빈번한 사용 캐시로 승격
     */
    private void promoteToFrequentIfNeeded(String key, Drawable icon) {
        int usage = getUsageCount(key);
        if (usage >= 10) {
            frequentCache.put(key, icon);
            Log.v(TAG, "Promoted to frequent cache: " + key + " (usage: " + usage + ")");
        }
    }
    
    /**
     * 사용 빈도 증가
     */
    private void incrementUsage(String key) {
        usageCount.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * 사용 빈도 조회
     */
    private int getUsageCount(String key) {
        AtomicInteger count = usageCount.get(key);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Drawable 크기 계산 (메모리 사용량 추정)
     */
    private int getDrawableSize(Drawable drawable) {
        // 대략적인 크기 추정 (실제로는 더 정교하게 계산 가능)
        return drawable.getIntrinsicWidth() * drawable.getIntrinsicHeight() * 4; // ARGB_8888
    }
    
    /**
     * 메모리 정리
     */
    public void trimMemory(int level) {
        switch (level) {
            case android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
                // 심각한 메모리 부족 - 대부분 정리
                memoryCache.evictAll();
                recentCache.trimToSize(RECENT_CACHE_SIZE / 4);
                break;
            case android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
                // 메모리 부족 - 일부 정리
                memoryCache.trimToSize(MEMORY_CACHE_SIZE / 2);
                recentCache.trimToSize(RECENT_CACHE_SIZE / 2);
                break;
            case android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                // UI 숨김 - 가벼운 정리
                memoryCache.trimToSize(MEMORY_CACHE_SIZE * 3 / 4);
                break;
        }
    }
    
    /**
     * 캐시 상태 정보
     */
    public String getCacheStatus() {
        return String.format("Frequent: %d/%d, Recent: %d/%d, Memory: %d/%d KB", 
                frequentCache.size(), FREQUENT_CACHE_SIZE,
                recentCache.size(), RECENT_CACHE_SIZE,
                memoryCache.size(), MEMORY_CACHE_SIZE / 1024);
    }
    
    /**
     * 아이콘 로딩 콜백 인터페이스
     */
    public interface IconLoadCallback {
        void onIconLoaded(String key, Drawable icon);
        void onIconLoadFailed(String key);
    }
}
