package fr.neamar.kiss.command;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * 사용자 액션을 위한 Command 인터페이스
 */
public interface UserAction {
    
    /**
     * 액션을 실행합니다.
     *
     * @param context 실행 컨텍스트
     * @return 실행 결과
     */
    @NonNull
    ActionResult execute(@NonNull Context context);
    
    /**
     * 액션이 실행 가능한지 확인합니다.
     *
     * @param context 확인할 컨텍스트
     * @return 실행 가능 여부
     */
    boolean canExecute(@NonNull Context context);
    
    /**
     * 액션의 설명을 반환합니다.
     *
     * @return 액션 설명
     */
    @NonNull
    String getDescription();
    
    /**
     * 액션의 고유 ID를 반환합니다.
     *
     * @return 액션 ID
     */
    @NonNull
    String getActionId();
    
    /**
     * 액션 실행 결과
     */
    class ActionResult {
        public final boolean success;
        public final String message;
        public final Throwable error;
        
        private ActionResult(boolean success, String message, Throwable error) {
            this.success = success;
            this.message = message != null ? message : "";
            this.error = error;
        }
        
        public static ActionResult success() {
            return new ActionResult(true, "", null);
        }
        
        public static ActionResult success(@NonNull String message) {
            return new ActionResult(true, message, null);
        }
        
        public static ActionResult failure(@NonNull String message) {
            return new ActionResult(false, message, null);
        }
        
        public static ActionResult failure(@NonNull String message, @NonNull Throwable error) {
            return new ActionResult(false, message, error);
        }
        
        @Override
        public String toString() {
            return String.format("ActionResult{success=%s, message='%s', hasError=%s}", 
                               success, message, error != null);
        }
    }
}
