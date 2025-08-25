package fr.neamar.kiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.nio.charset.Charset;

public class RootHandler {

    private static final String TAG = RootHandler.class.getSimpleName();

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private Boolean isRootAvailable = null;
    private Boolean isRootActivated = null;
    private ShizukuHandler shizukuHandler = null;

    RootHandler(Context ctx) {
        // Shizuku 핸들러 먼저 초기화
        shizukuHandler = new ShizukuHandler(ctx);
        // 그 다음 리셋 호출
        resetRootHandler(ctx);
    }

    public boolean isRootActivated() {
        return this.isRootActivated;
    }

    void resetRootHandler(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        isRootActivated = prefs.getBoolean("root-mode", false);
        
        // Shizuku 핸들러도 함께 리셋
        if (shizukuHandler != null) {
            shizukuHandler.resetShizukuHandler(ctx);
        }
    }

    /**
     * 루트 권한 또는 Shizuku 가용성 확인
     */
    public boolean isRootAvailable() {
        if (isRootAvailable == null) {
            try {
                isRootAvailable = executeRootShell(null);
            } catch (Exception e) {
                isRootAvailable = false;
            }
        }
        return isRootAvailable;
    }

    /**
     * Shizuku 가용성 확인
     */
    public boolean isShizukuAvailable() {
        return shizukuHandler != null && shizukuHandler.isShizukuAvailable();
    }

    /**
     * Shizuku 권한 확인
     */
    public boolean hasShizukuPermission() {
        return shizukuHandler != null && shizukuHandler.hasShizukuPermission();
    }

    /**
     * Shizuku 권한 요청
     */
    public void requestShizukuPermission() {
        if (shizukuHandler != null) {
            shizukuHandler.requestShizukuPermission();
        }
    }

    /**
     * Shizuku 활성화 상태 확인
     */
    public boolean isShizukuActivated() {
        return shizukuHandler != null && shizukuHandler.isShizukuActivated();
    }

    /**
     * Shizuku 상태 새로고침
     */
    public void refreshShizukuStatus() {
        if (shizukuHandler != null) {
            shizukuHandler.refreshShizukuStatus();
        }
    }

    /**
     * 앱 최대 절전 모드 - 루트 또는 Shizuku 사용
     */
    public boolean hibernateApp(String packageName) {
        // 1순위: Shizuku 사용 (더 안전하고 안정적)
        if (shizukuHandler != null && shizukuHandler.isShizukuActivated() && 
            shizukuHandler.isShizukuAvailable() && shizukuHandler.hasShizukuPermission()) {
            Log.d(TAG, "Hibernating app using Shizuku: " + packageName);
            return shizukuHandler.hibernateApp(packageName);
        }
        
        // 2순위: 전통적인 루트 권한 사용
        if (isRootActivated && isRootAvailable()) {
            Log.d(TAG, "Hibernating app using root: " + packageName);
            try {
                return executeRootShell("am force-stop " + packageName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to hibernate app using root: " + packageName, e);
                return false;
            }
        }
        
        Log.w(TAG, "Neither Shizuku nor root access available for hibernation");
        return false;
    }

    /**
     * 리소스 정리 (메모리 누수 방지)
     */
    public void destroy() {
        if (shizukuHandler != null) {
            shizukuHandler.removeShizukuListeners();
            shizukuHandler = null;
        }
    }

    private boolean executeRootShell(String command) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");
            //put command
            if (command != null && !command.trim().equals("")) {
                p.getOutputStream().write((command + "\n").getBytes(UTF_8));
            }
            //exit from su command
            p.getOutputStream().write("exit\n".getBytes(UTF_8));
            p.getOutputStream().flush();
            p.getOutputStream().close();
            int result = p.waitFor();
            if (result != 0)
                throw new Exception("Command execution failed " + result);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to execute root shell", e);
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        return false;
    }

}
