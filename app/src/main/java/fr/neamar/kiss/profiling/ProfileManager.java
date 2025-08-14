package fr.neamar.kiss.profiling;

import android.content.Context;
import android.util.Log;
import fr.neamar.kiss.BuildConfig;

/**
 * Profile 빌드에서만 활성화되는 성능 프로파일링 매니저
 */
public class ProfileManager {
    private static final String TAG = "ProfileManager";
    private static ProfileManager instance;
    private PerformanceProfiler profiler;
    private boolean isInitialized = false;
    
    private ProfileManager() {}
    
    public static synchronized ProfileManager getInstance() {
        if (instance == null) {
            instance = new ProfileManager();
        }
        return instance;
    }
    
    /**
     * Profile 모드에서만 프로파일러 초기화
     */
    public void initialize(Context context) {
        if (isInitialized) {
            return;
        }
        
        // Profile 빌드에서만 활성화
        if (isProfileBuild()) {
            profiler = PerformanceProfiler.getInstance(context);
            isInitialized = true;
            Log.i(TAG, "Performance profiler initialized for profile build");
        } else {
            Log.d(TAG, "Performance profiler disabled for non-profile build");
        }
    }
    
    /**
     * 프로파일링 시작
     */
    public void startProfiling() {
        if (profiler != null) {
            profiler.startProfiling();
            Log.i(TAG, "Profiling started. Logs will be saved to: " + profiler.getLogDirectoryPath());
        }
    }
    
    /**
     * 프로파일링 중지
     */
    public void stopProfiling() {
        if (profiler != null) {
            profiler.stopProfiling();
        }
    }
    
    /**
     * 커스텀 이벤트 로깅
     */
    public void logEvent(String eventName, String details) {
        if (profiler != null) {
            profiler.logCustomEvent(eventName, details);
        }
    }
    
    /**
     * 앱 시작 이벤트 로깅
     */
    public void logAppStart() {
        logEvent("APP_START", "Application started");
    }
    
    /**
     * 액티비티 생명주기 이벤트 로깅
     */
    public void logActivityLifecycle(String activityName, String lifecycle) {
        logEvent("ACTIVITY_LIFECYCLE", activityName + ":" + lifecycle);
    }
    
    /**
     * 검색 성능 이벤트 로깅
     */
    public void logSearchPerformance(String query, long durationMs, int resultCount) {
        logEvent("SEARCH_PERFORMANCE", 
                "query:" + query + ",duration:" + durationMs + "ms,results:" + resultCount);
    }
    
    /**
     * 메모리 압박 이벤트 로깅
     */
    public void logMemoryPressure(int level) {
        logEvent("MEMORY_PRESSURE", "level:" + level);
    }
    
    /**
     * Profile 빌드인지 확인
     */
    private boolean isProfileBuild() {
        // BuildConfig에서 빌드 타입 확인
        try {
            return BuildConfig.BUILD_TYPE.equals("profile") || 
                   BuildConfig.APPLICATION_ID.contains("profile");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 프로파일러가 활성화되었는지 확인
     */
    public boolean isProfilingEnabled() {
        return profiler != null && isInitialized;
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        if (profiler != null) {
            profiler.cleanup();
        }
    }
    
    /**
     * 로그 디렉토리 경로 반환
     */
    public String getLogDirectory() {
        if (profiler != null) {
            return profiler.getLogDirectoryPath();
        }
        return null;
    }
}
