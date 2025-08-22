package fr.neamar.kiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Shizuku를 활용한 시스템 레벨 작업 처리 클래스
 * 루트 권한 없이도 앱 강제 종료 등의 기능을 제공
 */
public class ShizukuHandler {

    private static final String TAG = ShizukuHandler.class.getSimpleName();

    private Boolean isShizukuAvailable = null;
    private Boolean isShizukuActivated = null;

    ShizukuHandler(Context ctx) {
        resetShizukuHandler(ctx);
    }

    public boolean isShizukuActivated() {
        return this.isShizukuActivated != null && this.isShizukuActivated;
    }

    void resetShizukuHandler(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        isShizukuActivated = prefs.getBoolean("shizuku-mode", false);
    }

    /**
     * Shizuku 서비스 가용성 확인
     */
    public boolean isShizukuAvailable() {
        if (isShizukuAvailable == null) {
            try {
                isShizukuAvailable = Shizuku.pingBinder();
            } catch (Exception e) {
                Log.e(TAG, "Failed to ping Shizuku binder", e);
                isShizukuAvailable = false;
            }
        }
        return isShizukuAvailable;
    }

    /**
     * Shizuku 권한 확인
     */
    public boolean hasShizukuPermission() {
        if (!isShizukuAvailable()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "Failed to check Shizuku permission", e);
            return false;
        }
    }

    /**
     * Shizuku 권한 요청
     */
    public void requestShizukuPermission() {
        if (isShizukuAvailable() && !hasShizukuPermission()) {
            try {
                Shizuku.requestPermission(0);
            } catch (Exception e) {
                Log.e(TAG, "Failed to request Shizuku permission", e);
            }
        }
    }

    /**
     * Shizuku를 통한 앱 강제 종료
     * @param packageName 종료할 앱의 패키지명
     * @return 성공 여부
     */
    public boolean hibernateApp(String packageName) {
        if (!isShizukuActivated() || !isShizukuAvailable() || !hasShizukuPermission()) {
            Log.w(TAG, "Shizuku not available or permission not granted");
            return false;
        }

        try {
            return forceStopPackage(packageName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hibernate app: " + packageName, e);
            return false;
        }
    }

    /**
     * ActivityManager를 통한 앱 강제 종료
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean forceStopPackage(String packageName) {
        try {
            // Shizuku를 통해 시스템 서비스에 접근
            Object activityManager = SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE);
            
            if (activityManager == null) {
                Log.e(TAG, "Failed to get ActivityManager service");
                return false;
            }

            // forceStopPackage 메서드 호출
            Method forceStopMethod = activityManager.getClass().getMethod("forceStopPackage", String.class);
            forceStopMethod.invoke(activityManager, packageName);
            
            Log.i(TAG, "Successfully hibernated app: " + packageName);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to force stop package: " + packageName, e);
            return false;
        }
    }

    /**
     * 대안 방법: Shell 명령어를 통한 앱 종료
     */
    private boolean forceStopWithShell(String packageName) {
        try {
            // Shizuku.newProcess는 deprecated이고 private이므로, 대신 다른 방법 사용
            // ActivityManager 방식이 실패하면 false 반환
            Log.w(TAG, "Shell command method not available, using ActivityManager only");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to execute shell command for: " + packageName, e);
            return false;
        }
    }

    /**
     * Shizuku 서비스 상태를 다시 확인
     */
    public void refreshShizukuStatus() {
        isShizukuAvailable = null;
        isShizukuAvailable();
    }
}
