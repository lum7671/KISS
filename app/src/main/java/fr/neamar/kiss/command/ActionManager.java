package fr.neamar.kiss.command;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사용자 액션을 관리하는 매니저
 */
public class ActionManager {
    
    private static final String TAG = ActionManager.class.getSimpleName();
    
    private static ActionManager instance;
    
    private final Map<String, UserAction> registeredActions = new ConcurrentHashMap<>();
    private final List<ActionExecutionListener> listeners = new ArrayList<>();
    
    // 액션 실행 리스너 인터페이스
    public interface ActionExecutionListener {
        void onActionStarted(@NonNull UserAction action);
        void onActionCompleted(@NonNull UserAction action, @NonNull UserAction.ActionResult result);
        void onActionFailed(@NonNull UserAction action, @NonNull UserAction.ActionResult result);
    }
    
    private ActionManager() {
        // 싱글톤
    }
    
    public static synchronized ActionManager getInstance() {
        if (instance == null) {
            instance = new ActionManager();
        }
        return instance;
    }
    
    /**
     * 액션을 등록합니다.
     *
     * @param action 등록할 액션
     */
    public void registerAction(@NonNull UserAction action) {
        String actionId = action.getActionId();
        registeredActions.put(actionId, action);
        Log.d(TAG, "Action registered: " + actionId);
    }
    
    /**
     * 액션을 해제합니다.
     *
     * @param actionId 해제할 액션 ID
     */
    public void unregisterAction(@NonNull String actionId) {
        UserAction removed = registeredActions.remove(actionId);
        if (removed != null) {
            Log.d(TAG, "Action unregistered: " + actionId);
        }
    }
    
    /**
     * 액션을 실행합니다.
     *
     * @param context  실행 컨텍스트
     * @param actionId 실행할 액션 ID
     * @return 실행 결과
     */
    @NonNull
    public UserAction.ActionResult executeAction(@NonNull Context context, @NonNull String actionId) {
        UserAction action = registeredActions.get(actionId);
        if (action == null) {
            String message = "Action not found: " + actionId;
            Log.w(TAG, message);
            return UserAction.ActionResult.failure(message);
        }
        
        return executeAction(context, action);
    }
    
    /**
     * 액션을 실행합니다.
     *
     * @param context 실행 컨텍스트
     * @param action  실행할 액션
     * @return 실행 결과
     */
    @NonNull
    public UserAction.ActionResult executeAction(@NonNull Context context, @NonNull UserAction action) {
        Log.d(TAG, "Executing action: " + action.getActionId());
        
        // 실행 가능 여부 확인
        if (!action.canExecute(context)) {
            String message = "Action cannot be executed: " + action.getActionId();
            Log.w(TAG, message);
            UserAction.ActionResult result = UserAction.ActionResult.failure(message);
            notifyActionFailed(action, result);
            return result;
        }
        
        // 실행 시작 알림
        notifyActionStarted(action);
        
        try {
            // 액션 실행
            UserAction.ActionResult result = action.execute(context);
            
            // 결과에 따른 알림
            if (result.success) {
                Log.d(TAG, "Action completed successfully: " + action.getActionId());
                notifyActionCompleted(action, result);
            } else {
                Log.w(TAG, "Action failed: " + action.getActionId() + " - " + result.message);
                notifyActionFailed(action, result);
            }
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Exception during action execution: " + action.getActionId(), e);
            UserAction.ActionResult result = UserAction.ActionResult.failure(
                "액션 실행 중 예외 발생: " + e.getMessage(), e);
            notifyActionFailed(action, result);
            return result;
        }
    }
    
    /**
     * 등록된 액션을 조회합니다.
     *
     * @param actionId 조회할 액션 ID
     * @return 액션 또는 null
     */
    @Nullable
    public UserAction getAction(@NonNull String actionId) {
        return registeredActions.get(actionId);
    }
    
    /**
     * 모든 등록된 액션을 반환합니다.
     *
     * @return 등록된 액션들의 복사본
     */
    @NonNull
    public Map<String, UserAction> getAllActions() {
        return new HashMap<>(registeredActions);
    }
    
    /**
     * 실행 가능한 액션들만 필터링해서 반환합니다.
     *
     * @param context 확인할 컨텍스트
     * @return 실행 가능한 액션들
     */
    @NonNull
    public List<UserAction> getExecutableActions(@NonNull Context context) {
        List<UserAction> executable = new ArrayList<>();
        for (UserAction action : registeredActions.values()) {
            if (action.canExecute(context)) {
                executable.add(action);
            }
        }
        return executable;
    }
    
    /**
     * 액션 실행 리스너를 추가합니다.
     *
     * @param listener 추가할 리스너
     */
    public void addActionExecutionListener(@NonNull ActionExecutionListener listener) {
        synchronized (listeners) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }
    
    /**
     * 액션 실행 리스너를 제거합니다.
     *
     * @param listener 제거할 리스너
     */
    public void removeActionExecutionListener(@NonNull ActionExecutionListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }
    
    /**
     * 모든 액션과 리스너를 정리합니다.
     */
    public void cleanup() {
        registeredActions.clear();
        synchronized (listeners) {
            listeners.clear();
        }
        Log.d(TAG, "ActionManager cleaned up");
    }
    
    // 알림 메서드들
    private void notifyActionStarted(@NonNull UserAction action) {
        synchronized (listeners) {
            for (ActionExecutionListener listener : listeners) {
                try {
                    listener.onActionStarted(action);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying action started", e);
                }
            }
        }
    }
    
    private void notifyActionCompleted(@NonNull UserAction action, @NonNull UserAction.ActionResult result) {
        synchronized (listeners) {
            for (ActionExecutionListener listener : listeners) {
                try {
                    listener.onActionCompleted(action, result);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying action completed", e);
                }
            }
        }
    }
    
    private void notifyActionFailed(@NonNull UserAction action, @NonNull UserAction.ActionResult result) {
        synchronized (listeners) {
            for (ActionExecutionListener listener : listeners) {
                try {
                    listener.onActionFailed(action, result);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying action failed", e);
                }
            }
        }
    }
    
    /**
     * 액션 매니저 통계를 반환합니다.
     */
    public ActionStats getStats() {
        return new ActionStats(registeredActions.size(), listeners.size());
    }
    
    /**
     * 액션 매니저 통계 클래스
     */
    public static class ActionStats {
        public final int totalActions;
        public final int totalListeners;
        
        ActionStats(int totalActions, int totalListeners) {
            this.totalActions = totalActions;
            this.totalListeners = totalListeners;
        }
        
        @Override
        public String toString() {
            return String.format("ActionStats{actions=%d, listeners=%d}", 
                               totalActions, totalListeners);
        }
    }
}
