package fr.neamar.kiss.profiling;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

/**
 * 사용자 액션별 성능 추적기
 * 스크롤, 검색, 터치 등의 사용자 액션과 성능 지표를 연관지어 분석
 */
public class ActionPerformanceTracker {
    private static final String TAG = "ActionPerformanceTracker";
    private static ActionPerformanceTracker instance;
    
    private PerformanceProfiler profiler;
    private boolean isEnabled = false;
    
    // 액션 추적용 변수들
    private long lastActionTime = 0;
    private String currentAction = "IDLE";
    private long actionStartTime = 0;
    private long lastMemoryUsage = 0;
    private double lastCpuUsage = 0;
    
    private ActionPerformanceTracker() {}
    
    public static synchronized ActionPerformanceTracker getInstance() {
        if (instance == null) {
            instance = new ActionPerformanceTracker();
        }
        return instance;
    }
    
    public void initialize(Context context) {
        this.profiler = PerformanceProfiler.getInstance(context);
        this.isEnabled = true;
        Log.i(TAG, "Action performance tracker initialized");
    }
    
    /**
     * 사용자 액션 시작 추적
     */
    public void startAction(String actionName) {
        if (!isEnabled || profiler == null) return;
        
        currentAction = actionName;
        actionStartTime = SystemClock.elapsedRealtime();
        lastActionTime = System.currentTimeMillis();
        
        // 액션 시작 시점의 성능 지표 기록
        collectActionSnapshot("ACTION_START:" + actionName);
        
        Log.d(TAG, "Action started: " + actionName);
    }
    
    /**
     * 사용자 액션 종료 추적
     */
    public void endAction(String actionName) {
        if (!isEnabled || profiler == null) return;
        
        long actionDuration = SystemClock.elapsedRealtime() - actionStartTime;
        
        // 액션 종료 시점의 성능 지표 기록
        collectActionSnapshot("ACTION_END:" + actionName + ",duration:" + actionDuration + "ms");
        
        currentAction = "IDLE";
        
        Log.d(TAG, "Action completed: " + actionName + " (duration: " + actionDuration + "ms)");
    }
    
    /**
     * 스크롤 성능 추적
     */
    public void trackScrollAction(String direction, int itemCount, float velocity) {
        if (!isEnabled) return;
        
        String actionDetails = String.format("direction:%s,items:%d,velocity:%.2f", 
                                            direction, itemCount, velocity);
        
        profiler.logCustomEvent("SCROLL_ACTION", actionDetails);
        collectActionSnapshot("SCROLL_PERFORMANCE");
    }
    
    /**
     * 검색 성능 상세 추적
     */
    public void trackSearchAction(String query, int phase, int resultCount) {
        if (!isEnabled) return;
        
        String phaseInfo;
        switch (phase) {
            case 0: phaseInfo = "SEARCH_START"; break;
            case 1: phaseInfo = "SEARCH_TYPING"; break;
            case 2: phaseInfo = "SEARCH_COMPLETE"; break;
            default: phaseInfo = "SEARCH_UPDATE"; break;
        }
        
        String actionDetails = String.format("query_length:%d,phase:%s,results:%d", 
                                            query.length(), phaseInfo, resultCount);
        
        profiler.logCustomEvent("SEARCH_DETAILED", actionDetails);
        collectActionSnapshot(phaseInfo);
    }
    
    /**
     * UI 상호작용 추적 (버튼 클릭, 스와이프 등)
     */
    public void trackUIInteraction(String interactionType, String target, long responseTime) {
        if (!isEnabled) return;
        
        String actionDetails = String.format("type:%s,target:%s,response_time:%dms", 
                                            interactionType, target, responseTime);
        
        profiler.logCustomEvent("UI_INTERACTION", actionDetails);
        collectActionSnapshot("UI_RESPONSE");
    }
    
    /**
     * 앱 시작 성능 상세 추적
     */
    public void trackAppStartupPhase(String phase, long phaseTime) {
        if (!isEnabled) return;
        
        String actionDetails = String.format("phase:%s,time:%dms", phase, phaseTime);
        profiler.logCustomEvent("STARTUP_PHASE", actionDetails);
        collectActionSnapshot("STARTUP_" + phase);
    }
    
    /**
     * 메모리 할당/해제 이벤트 추적
     */
    public void trackMemoryEvent(String eventType, long memoryChange) {
        if (!isEnabled) return;
        
        String actionDetails = String.format("event:%s,change:%dKB", eventType, memoryChange);
        profiler.logCustomEvent("MEMORY_EVENT", actionDetails);
        collectActionSnapshot("MEMORY_" + eventType);
    }
    
    /**
     * 액션 수행 중 성능 스냅샷 수집
     */
    private void collectActionSnapshot(String context) {
        if (!isEnabled || profiler == null) return;
        
        try {
            // 현재 성능 지표 수집
            Runtime runtime = Runtime.getRuntime();
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            int threadCount = Thread.activeCount();
            
            // 메모리 변화량 계산
            long memoryDelta = currentMemory - lastMemoryUsage;
            lastMemoryUsage = currentMemory;
            
            String snapshotDetails = String.format(
                "context:%s,memory_mb:%.2f,memory_delta:%.2f,threads:%d,action:%s",
                context,
                currentMemory / (1024.0 * 1024.0),
                memoryDelta / (1024.0 * 1024.0),
                threadCount,
                currentAction
            );
            
            profiler.logCustomEvent("PERFORMANCE_SNAPSHOT", snapshotDetails);
            
        } catch (Exception e) {
            Log.w(TAG, "Error collecting action snapshot", e);
        }
    }
    
    /**
     * 특정 시간 간격으로 성능 변화 추적
     */
    public void trackPerformanceSpike(String triggerAction) {
        if (!isEnabled) return;
        
        // 성능 급변 상황 감지 및 로깅
        Runtime runtime = Runtime.getRuntime();
        long currentMemory = runtime.totalMemory() - runtime.freeMemory();
        
        if (lastMemoryUsage > 0) {
            double memoryGrowthRate = ((double)(currentMemory - lastMemoryUsage) / lastMemoryUsage) * 100;
            
            if (Math.abs(memoryGrowthRate) > 20) { // 20% 이상 메모리 변화
                String spikeDetails = String.format("trigger:%s,growth_rate:%.2f%%,current_mb:%.2f",
                                                   triggerAction, memoryGrowthRate, 
                                                   currentMemory / (1024.0 * 1024.0));
                profiler.logCustomEvent("PERFORMANCE_SPIKE", spikeDetails);
            }
        }
    }
    
    /**
     * 연속된 액션 패턴 추적 (예: 빠른 스크롤 연속)
     */
    public void trackActionPattern(String actionType, int sequenceCount, long totalDuration) {
        if (!isEnabled) return;
        
        String patternDetails = String.format("action:%s,count:%d,total_duration:%dms,avg_duration:%.2f",
                                             actionType, sequenceCount, totalDuration,
                                             (double)totalDuration / sequenceCount);
        
        profiler.logCustomEvent("ACTION_PATTERN", patternDetails);
        collectActionSnapshot("PATTERN_" + actionType);
    }
    
    /**
     * 백그라운드/포그라운드 전환 성능 추적
     */
    public void trackAppStateChange(String newState, long transitionTime) {
        if (!isEnabled) return;
        
        String stateDetails = String.format("new_state:%s,transition_time:%dms", newState, transitionTime);
        profiler.logCustomEvent("APP_STATE_CHANGE", stateDetails);
        collectActionSnapshot("STATE_" + newState);
    }
    
    public boolean isEnabled() {
        return isEnabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}
