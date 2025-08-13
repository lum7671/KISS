package fr.neamar.kiss.refactored;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.command.ActionManager;
import fr.neamar.kiss.controller.LifecycleController;
import fr.neamar.kiss.controller.SearchController;
import fr.neamar.kiss.controller.UIController;

/**
 * 리팩터링된 MainActivity 예시
 * 
 * 기존의 거대한 MainActivity를 여러 컨트롤러로 분리하여
 * 단일 책임 원칙을 적용한 구조입니다.
 */
public class RefactoredMainActivity extends MainActivity {
    
    private static final String TAG = RefactoredMainActivity.class.getSimpleName();
    
    // 컨트롤러들
    private SearchController searchController;
    private UIController uiController;
    private LifecycleController lifecycleController;
    
    // 액션 매니저
    private ActionManager actionManager;
    
    // 설정
    private SharedPreferences prefs;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate() - Refactored");
        
        // 기본 설정
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // 액션 매니저 초기화
        actionManager = ActionManager.getInstance();
        
        // 컨트롤러들 초기화
        initializeControllers();
        
        // 레이아웃 설정
        // setContentView(R.layout.main);
        
        // 컨트롤러들 생명주기 시작
        lifecycleController.onCreate();
        
        Log.i(TAG, "RefactoredMainActivity initialized successfully");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        lifecycleController.onResume();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        lifecycleController.onPause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 컨트롤러들 정리
        cleanupControllers();
        
        Log.d(TAG, "RefactoredMainActivity destroyed");
    }
    
    /**
     * 컨트롤러들을 초기화합니다.
     */
    private void initializeControllers() {
        Log.d(TAG, "Initializing controllers");
        
        // 검색 컨트롤러 초기화
        searchController = new SearchController(this);
        setupSearchController();
        
        // UI 컨트롤러 초기화  
        uiController = new UIController(this, prefs);
        setupUIController();
        
        // 생명주기 컨트롤러 초기화
        lifecycleController = new LifecycleController(this, prefs);
        setupLifecycleController();
        
        Log.i(TAG, "All controllers initialized");
    }
    
    /**
     * 검색 컨트롤러를 설정합니다.
     */
    private void setupSearchController() {
        searchController.addSearchListener(new SearchController.SearchListener() {
            @Override
            public void onSearchStarted(@NonNull String query) {
                Log.d(TAG, "Search started: " + query);
                // UI 업데이트 (로딩 표시 등)
            }
            
            @Override
            public void onSearchCompleted(@NonNull String query, @NonNull java.util.List<?> results) {
                Log.d(TAG, "Search completed: " + query + " (" + results.size() + " results)");
                // 결과 표시
                updateSearchResults(results);
            }
            
            @Override
            public void onSearchCancelled(@NonNull String query) {
                Log.d(TAG, "Search cancelled: " + query);
            }
            
            @Override
            public void onSearchError(@NonNull String query, @NonNull Throwable error) {
                Log.e(TAG, "Search error: " + query, error);
                // 오류 처리
            }
        });
    }
    
    /**
     * UI 컨트롤러를 설정합니다.
     */
    private void setupUIController() {
        uiController.addUIStateListener(new UIController.UIStateListener() {
            @Override
            public void onKissBarVisibilityChanged(boolean visible) {
                Log.d(TAG, "KISS bar visibility: " + visible);
            }
            
            @Override
            public void onKeyboardVisibilityChanged(boolean visible) {
                Log.d(TAG, "Keyboard visibility: " + visible);
            }
            
            @Override
            public void onThemeChanged() {
                Log.d(TAG, "Theme changed");
            }
            
            @Override
            public void onLayoutChanged() {
                Log.d(TAG, "Layout changed");
            }
        });
    }
    
    /**
     * 생명주기 컨트롤러를 설정합니다.
     */
    private void setupLifecycleController() {
        lifecycleController.addLifecycleListener(new LifecycleController.LifecycleListener() {
            @Override
            public void onResume() {
                Log.d(TAG, "Lifecycle: Resume");
                // 데이터 새로고침 등
            }
            
            @Override
            public void onPause() {
                Log.d(TAG, "Lifecycle: Pause");
                // 상태 저장 등
            }
            
            @Override
            public void onScreenOn() {
                Log.d(TAG, "Lifecycle: Screen ON");
                // 화면 켜짐 처리
            }
            
            @Override
            public void onScreenOff() {
                Log.d(TAG, "Lifecycle: Screen OFF");
                // 화면 꺼짐 처리
            }
            
            @Override
            public void onRecreateNeeded() {
                Log.d(TAG, "Lifecycle: Recreate needed");
                // 액티비티 재구성 처리
            }
        });
    }
    
    /**
     * 검색 결과를 업데이트합니다.
     */
    private void updateSearchResults(@NonNull java.util.List<?> results) {
        // 어댑터 업데이트 로직
        Log.d(TAG, "Updating search results: " + results.size() + " items");
    }
    
    /**
     * 컨트롤러들을 정리합니다.
     */
    private void cleanupControllers() {
        Log.d(TAG, "Cleaning up controllers");
        
        if (searchController != null) {
            searchController.cleanup();
        }
        
        if (uiController != null) {
            uiController.cleanup();
        }
        
        if (lifecycleController != null) {
            lifecycleController.cleanup();
        }
        
        Log.i(TAG, "All controllers cleaned up");
    }
    
    // QueryInterface 구현
    public void updateSearchRecords(String query) {
        // 검색 결과 업데이트
    }
    
    // 공개 API - 다른 컴포넌트에서 사용할 수 있는 메서드들
    
    /**
     * 검색을 수행합니다.
     */
    public void performSearch(@NonNull String query) {
        if (searchController != null) {
            searchController.performSearch(query);
        }
    }
    
    /**
     * KISS 바를 표시합니다.
     */
    public void showKissBar(boolean animate) {
        if (uiController != null) {
            uiController.showKissBar(animate);
        }
    }
    
    /**
     * KISS 바를 숨깁니다.
     */
    public void hideKissBar() {
        if (uiController != null) {
            uiController.hideKissBar();
        }
    }
    
    /**
     * 테마를 업데이트합니다.
     */
    public void updateTheme() {
        if (uiController != null) {
            uiController.updateTheme();
        }
    }
    
    /**
     * 액티비티 상태 정보를 반환합니다.
     */
    public ActivityStatus getStatus() {
        return new ActivityStatus(
            searchController != null ? searchController.getCurrentQuery() : "",
            searchController != null ? searchController.isSearching() : false,
            uiController != null ? uiController.isDisplayingKissBar() : false,
            lifecycleController != null ? lifecycleController.isScreenOn() : true
        );
    }
    
    /**
     * 액티비티 상태 정보 클래스
     */
    public static class ActivityStatus {
        public final String currentQuery;
        public final boolean isSearching;
        public final boolean isKissBarVisible;
        public final boolean isScreenOn;
        
        ActivityStatus(String currentQuery, boolean isSearching, 
                      boolean isKissBarVisible, boolean isScreenOn) {
            this.currentQuery = currentQuery;
            this.isSearching = isSearching;
            this.isKissBarVisible = isKissBarVisible;
            this.isScreenOn = isScreenOn;
        }
        
        @Override
        public String toString() {
            return String.format("ActivityStatus{query='%s', searching=%s, kissBar=%s, screen=%s}", 
                               currentQuery, isSearching, isKissBarVisible, isScreenOn);
        }
    }
}
