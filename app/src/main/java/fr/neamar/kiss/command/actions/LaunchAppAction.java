package fr.neamar.kiss.command.actions;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

import fr.neamar.kiss.command.UserAction;
import fr.neamar.kiss.pojo.AppPojo;

/**
 * 앱을 실행하는 액션
 */
public class LaunchAppAction implements UserAction {
    
    private static final String TAG = LaunchAppAction.class.getSimpleName();
    private static final String ACTION_ID = "launch_app";
    
    private final AppPojo appPojo;
    
    public LaunchAppAction(@NonNull AppPojo appPojo) {
        this.appPojo = appPojo;
    }
    
    @NonNull
    @Override
    public ActionResult execute(@NonNull Context context) {
        try {
            Log.d(TAG, "Launching app: " + appPojo.getName());
            
            // Intent 생성
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setComponent(new ComponentName(appPojo.packageName, appPojo.activityName));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            
            // 멀티 유저 지원
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                context.startActivity(intent);
            } else {
                context.startActivity(intent);
            }
            
            Log.i(TAG, "Successfully launched app: " + appPojo.getName());
            return ActionResult.success("앱이 실행되었습니다: " + appPojo.getName());
            
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception launching app: " + appPojo.getName(), e);
            return ActionResult.failure("앱 실행 권한이 없습니다: " + appPojo.getName(), e);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app: " + appPojo.getName(), e);
            return ActionResult.failure("앱 실행 중 오류가 발생했습니다: " + appPojo.getName(), e);
        }
    }
    
    @Override
    public boolean canExecute(@NonNull Context context) {
        // 앱이 설치되어 있고 활성화되어 있는지 확인
        if (appPojo.isDisabled()) {
            return false;
        }
        
        try {
            PackageManager pm = context.getPackageManager();
            pm.getApplicationInfo(appPojo.packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "App not found: " + appPojo.packageName);
            return false;
        }
    }
    
    @NonNull
    @Override
    public String getDescription() {
        return "앱 실행: " + appPojo.getName();
    }
    
    @NonNull
    @Override
    public String getActionId() {
        return ACTION_ID + "_" + appPojo.id;
    }
    
    public AppPojo getAppPojo() {
        return appPojo;
    }
}
