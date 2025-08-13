package fr.neamar.kiss.controller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import fr.neamar.kiss.MainActivity;

/**
 * 액티비티 생명주기 및 시스템 이벤트를 관리하는 컨트롤러
 */
public class LifecycleController {
    
    private static final String TAG = LifecycleController.class.getSimpleName();
    
    private final MainActivity mainActivity;
    private final SharedPreferences prefs;
    private final Handler mainHandler;
    
    // 상태 추적
    private boolean isScreenOn = true;
    private boolean isResumed = false;
    private long lastResumeTime = 0;
    private long lastRecreateTime = 0;
    
    // 화면 상태 리시버
    private BroadcastReceiver screenStateReceiver;
    
    // 생명주기 리스너 인터페이스
    public interface LifecycleListener {
        void onResume();
        void onPause();
        void onScreenOn();
        void onScreenOff();
        void onRecreateNeeded();
    }
    
    private final List<LifecycleListener> listeners = new ArrayList<>();
    
    public LifecycleController(@NonNull MainActivity mainActivity, @NonNull SharedPreferences prefs) {
        this.mainActivity = mainActivity;
        this.prefs = prefs;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        initializeScreenStateReceiver();
    }
    
    /**
     * 액티비티가 생성될 때 호출됩니다.
     */
    public void onCreate() {
        Log.d(TAG, "Activity created");
        registerScreenStateReceiver();
    }
    
    /**
     * 액티비티가 재개될 때 호출됩니다.
     */
    public void onResume() {
        Log.d(TAG, "Activity resumed");
        isResumed = true;
        lastResumeTime = System.currentTimeMillis();
        
        // 지연된 레이아웃 업데이트 확인
        checkDelayedLayoutUpdate();
        
        notifyResume();
    }
    
    /**
     * 액티비티가 일시 중지될 때 호출됩니다.
     */
    public void onPause() {
        Log.d(TAG, "Activity paused");
        isResumed = false;
        
        notifyPause();
    }
    
    /**
     * 액티비티가 소멸될 때 호출됩니다.
     */
    public void onDestroy() {
        Log.d(TAG, "Activity destroyed");
        
        // 리시버 해제
        unregisterScreenStateReceiver();
        
        // 리소스 정리
        cleanup();
    }
    
    /**
     * 액티비티 재구성이 필요한지 스마트하게 판단합니다.
     *
     * @return 재구성 필요 여부
     */
    public boolean shouldRecreateActivity() {
        // 1. 레이아웃 업데이트 플래그 확인
        boolean requireLayoutUpdate = prefs.getBoolean("require-layout-update", false);
        if (!requireLayoutUpdate) {
            return false;
        }
        
        // 2. 너무 빈번한 재구성 방지 (5초 이내 재구성 방지)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRecreateTime < 5000) {
            Log.d(TAG, "Preventing frequent recreation, waiting...");
            return false;
        }
        
        // 3. 화면이 꺼진 상태에서는 재구성하지 않음
        if (!isScreenOn) {
            Log.d(TAG, "Screen off, delaying recreation");
            return false;
        }
        
        // 4. 액티비티가 일시 중지된 상태에서는 재구성하지 않음
        if (!isResumed) {
            Log.d(TAG, "Activity not resumed, delaying recreation");
            return false;
        }
        
        lastRecreateTime = currentTime;
        return true;
    }
    
    /**
     * 액티비티를 재구성합니다.
     */
    public void recreateActivity() {
        if (shouldRecreateActivity()) {
            Log.i(TAG, "Recreating activity due to layout update requirement");
            
            // 플래그 제거
            prefs.edit().putBoolean("require-layout-update", false).apply();
            
            // 재구성 알림
            notifyRecreateNeeded();
            
            // 액티비티 재구성
            mainActivity.recreate();
        }
    }
    
    /**
     * 지연된 레이아웃 업데이트를 처리합니다.
     */
    private void checkDelayedLayoutUpdate() {
        if (prefs.getBoolean("require-layout-update", false) && isScreenOn) {
            Log.i(TAG, "Processing delayed layout update");
            
            // 500ms 지연 후 처리
            mainHandler.postDelayed(() -> {
                if (isResumed && isScreenOn) {
                    recreateActivity();
                }
            }, 500);
        }
    }
    
    /**
     * 화면 상태 모니터링 리시버를 초기화합니다.
     */
    private void initializeScreenStateReceiver() {
        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    onScreenTurnedOn();
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    onScreenTurnedOff();
                }
            }
        };
    }
    
    /**
     * 화면 상태 리시버를 등록합니다.
     */
    private void registerScreenStateReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            mainActivity.registerReceiver(screenStateReceiver, filter);
            Log.d(TAG, "Screen state receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register screen state receiver", e);
        }
    }
    
    /**
     * 화면 상태 리시버를 해제합니다.
     */
    private void unregisterScreenStateReceiver() {
        try {
            if (screenStateReceiver != null) {
                mainActivity.unregisterReceiver(screenStateReceiver);
                Log.d(TAG, "Screen state receiver unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to unregister screen state receiver", e);
        }
    }
    
    /**
     * 화면이 켜졌을 때 처리합니다.
     */
    private void onScreenTurnedOn() {
        Log.d(TAG, "Screen turned ON");
        isScreenOn = true;
        
        // 지연된 레이아웃 업데이트 처리
        checkDelayedLayoutUpdate();
        
        notifyScreenOn();
    }
    
    /**
     * 화면이 꺼졌을 때 처리합니다.
     */
    private void onScreenTurnedOff() {
        Log.d(TAG, "Screen turned OFF");
        isScreenOn = false;
        
        // 메모리 정리 등 최적화 수행
        performScreenOffOptimization();
        
        notifyScreenOff();
    }
    
    /**
     * 화면이 꺼졌을 때 최적화를 수행합니다.
     */
    private void performScreenOffOptimization() {
        // 메모리 정리
        System.gc();
        
        // 진행 중인 애니메이션 정리 등
        Log.d(TAG, "Screen off optimization performed");
    }
    
    // Getter 메서드들
    public boolean isScreenOn() {
        return isScreenOn;
    }
    
    public boolean isResumed() {
        return isResumed;
    }
    
    public long getLastResumeTime() {
        return lastResumeTime;
    }
    
    // 리스너 관리
    public void addLifecycleListener(@NonNull LifecycleListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeLifecycleListener(@NonNull LifecycleListener listener) {
        listeners.remove(listener);
    }
    
    public void clearLifecycleListeners() {
        listeners.clear();
    }
    
    // 알림 메서드들
    private void notifyResume() {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onResume();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying resume", e);
            }
        }
    }
    
    private void notifyPause() {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onPause();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying pause", e);
            }
        }
    }
    
    private void notifyScreenOn() {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onScreenOn();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying screen on", e);
            }
        }
    }
    
    private void notifyScreenOff() {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onScreenOff();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying screen off", e);
            }
        }
    }
    
    private void notifyRecreateNeeded() {
        for (LifecycleListener listener : listeners) {
            try {
                listener.onRecreateNeeded();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying recreate needed", e);
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        clearLifecycleListeners();
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "LifecycleController cleaned up");
    }
}
