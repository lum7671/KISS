package fr.neamar.kiss.controller;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import fr.neamar.kiss.searcher.Searcher;
import fr.neamar.kiss.searcher.QueryInterface;

/**
 * 검색 기능을 관리하는 컨트롤러
 */
public class SearchController {
    
    private static final String TAG = SearchController.class.getSimpleName();
    
    private final QueryInterface queryInterface;
    private Searcher currentSearchTask;
    private String currentQuery = "";
    
    // 검색 리스너 인터페이스
    public interface SearchListener {
        void onSearchStarted(@NonNull String query);
        void onSearchCompleted(@NonNull String query, @NonNull List<?> results);
        void onSearchCancelled(@NonNull String query);
        void onSearchError(@NonNull String query, @NonNull Throwable error);
    }
    
    private final List<SearchListener> listeners = new ArrayList<>();
    
    public SearchController(@NonNull QueryInterface queryInterface) {
        this.queryInterface = queryInterface;
    }
    
    /**
     * 검색을 수행합니다.
     *
     * @param query 검색 쿼리
     */
    public void performSearch(@NonNull String query) {
        String trimmedQuery = query.trim();
        
        // 이전 검색과 동일한 경우 무시
        if (trimmedQuery.equals(currentQuery)) {
            Log.d(TAG, "Ignoring duplicate search: " + query);
            return;
        }
        
        // 이전 검색 취소
        cancelCurrentSearch();
        
        currentQuery = trimmedQuery;
        
        // 빈 쿼리 처리
        if (TextUtils.isEmpty(trimmedQuery)) {
            notifySearchCompleted(trimmedQuery, new ArrayList<>());
            return;
        }
        
        // 검색 시작 알림
        notifySearchStarted(trimmedQuery);
        
        try {
            // 새로운 검색 시작 - QuerySearcher 사용
            if (queryInterface instanceof fr.neamar.kiss.MainActivity) {
                currentSearchTask = new fr.neamar.kiss.searcher.QuerySearcher((fr.neamar.kiss.MainActivity) queryInterface, trimmedQuery, false);
                currentSearchTask.executeOnExecutor(fr.neamar.kiss.searcher.Searcher.SEARCH_THREAD);
                
                Log.d(TAG, "Search started for: " + trimmedQuery);
            } else {
                Log.e(TAG, "QueryInterface is not MainActivity instance");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting search for: " + trimmedQuery, e);
            notifySearchError(trimmedQuery, e);
        }
    }
    
    /**
     * 현재 진행 중인 검색을 취소합니다.
     */
    public void cancelCurrentSearch() {
        if (currentSearchTask != null && !currentSearchTask.isCancelled()) {
            currentSearchTask.cancel(true);
            notifySearchCancelled(currentQuery);
            Log.d(TAG, "Cancelled search for: " + currentQuery);
        }
        currentSearchTask = null;
    }
    
    /**
     * 검색을 지웁니다.
     */
    public void clearSearch() {
        cancelCurrentSearch();
        currentQuery = "";
        notifySearchCompleted("", new ArrayList<>());
        Log.d(TAG, "Search cleared");
    }
    
    /**
     * 현재 검색 쿼리를 반환합니다.
     *
     * @return 현재 검색 쿼리
     */
    @NonNull
    public String getCurrentQuery() {
        return currentQuery;
    }
    
    /**
     * 검색이 진행 중인지 확인합니다.
     *
     * @return 검색 진행 중 여부
     */
    public boolean isSearching() {
        return currentSearchTask != null && !currentSearchTask.isCancelled();
    }
    
    /**
     * 검색 리스너를 추가합니다.
     *
     * @param listener 추가할 리스너
     */
    public void addSearchListener(@NonNull SearchListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * 검색 리스너를 제거합니다.
     *
     * @param listener 제거할 리스너
     */
    public void removeSearchListener(@NonNull SearchListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * 모든 검색 리스너를 제거합니다.
     */
    public void clearSearchListeners() {
        listeners.clear();
    }
    
    // 내부 알림 메서드들
    private void notifySearchStarted(@NonNull String query) {
        for (SearchListener listener : listeners) {
            try {
                listener.onSearchStarted(query);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying search started", e);
            }
        }
    }
    
    private void notifySearchCompleted(@NonNull String query, @NonNull List<?> results) {
        for (SearchListener listener : listeners) {
            try {
                listener.onSearchCompleted(query, results);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying search completed", e);
            }
        }
    }
    
    private void notifySearchCancelled(@NonNull String query) {
        for (SearchListener listener : listeners) {
            try {
                listener.onSearchCancelled(query);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying search cancelled", e);
            }
        }
    }
    
    private void notifySearchError(@NonNull String query, @NonNull Throwable error) {
        for (SearchListener listener : listeners) {
            try {
                listener.onSearchError(query, error);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying search error", e);
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        cancelCurrentSearch();
        clearSearchListeners();
        Log.d(TAG, "SearchController cleaned up");
    }
}
