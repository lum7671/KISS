package fr.neamar.kiss;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
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
    private static final int SHIZUKU_PERMISSION_REQUEST_CODE = 1001;

    private Boolean isShizukuAvailable = null;
    private Boolean isShizukuActivated = null;
    private Context context;

    // Shizuku 권한 변경 리스너
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            Log.d(TAG, "Permission request result: requestCode=" + requestCode + ", result=" + grantResult);
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
                Log.i(TAG, "Shizuku permission " + (granted ? "granted" : "denied"));
                // 권한 상태 변경 시 상태 새로고침
                refreshShizukuStatus();
            }
        }
    };

    // Shizuku 바인더 연결 상태 리스너
    private final Shizuku.OnBinderReceivedListener BINDER_RECEIVED_LISTENER = new Shizuku.OnBinderReceivedListener() {
        @Override
        public void onBinderReceived() {
            Log.d(TAG, "Shizuku binder received");
            refreshShizukuStatus();
        }
    };

    // Shizuku 바인더 연결 해제 리스너
    private final Shizuku.OnBinderDeadListener BINDER_DEAD_LISTENER = new Shizuku.OnBinderDeadListener() {
        @Override
        public void onBinderDead() {
            Log.w(TAG, "Shizuku binder died");
            isShizukuAvailable = false;
        }
    };

    ShizukuHandler(Context ctx) {
        this.context = ctx.getApplicationContext();
        try {
            // Shizuku 리스너 등록
            addShizukuListeners();
            resetShizukuHandler(ctx);
            Log.d(TAG, "ShizukuHandler initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ShizukuHandler", e);
            isShizukuActivated = false;
        }
    }

    /**
     * Shizuku 리스너를 추가합니다
     */
    private void addShizukuListeners() {
        try {
            Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
            Shizuku.addBinderReceivedListener(BINDER_RECEIVED_LISTENER);
            Shizuku.addBinderDeadListener(BINDER_DEAD_LISTENER);
            Log.d(TAG, "Shizuku listeners added");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add Shizuku listeners", e);
        }
    }

    /**
     * Shizuku 리스너를 제거합니다 (메모리 누수 방지)
     */
    public void removeShizukuListeners() {
        try {
            Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
            Shizuku.removeBinderReceivedListener(BINDER_RECEIVED_LISTENER);
            Shizuku.removeBinderDeadListener(BINDER_DEAD_LISTENER);
            Log.d(TAG, "Shizuku listeners removed");
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove Shizuku listeners", e);
        }
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
                // Shizuku API v11 이전 버전은 지원하지 않음
                if (Shizuku.isPreV11()) {
                    Log.w(TAG, "❌ Shizuku pre-v11 is not supported");
                    isShizukuAvailable = false;
                    return false;
                }
                
                // 먼저 Shizuku가 설치되어 있는지 확인
                if (!isShizukuInstalled()) {
                    Log.w(TAG, "❌ Shizuku app is not installed");
                    isShizukuAvailable = false;
                    return false;
                }
                
                Log.d(TAG, "✅ Shizuku app is installed, checking service availability...");
                
                // 바인더 상태 확인
                isShizukuAvailable = Shizuku.pingBinder();
                if (isShizukuAvailable) {
                    Log.i(TAG, "✅ Shizuku service is available and responding");
                } else {
                    Log.w(TAG, "❌ Shizuku.pingBinder() returned false - service not responding");
                    Log.w(TAG, "   Please check:");
                    Log.w(TAG, "   1. Shizuku service is started");
                    Log.w(TAG, "   2. ADB wireless debugging is connected (non-root)");
                    Log.w(TAG, "   3. Device is properly rooted (root mode)");
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "❌ Shizuku IllegalStateException: " + e.getMessage(), e);
                Log.e(TAG, "   This usually means Shizuku API not properly initialized");
                Log.e(TAG, "   Check if ShizukuProvider is properly configured in AndroidManifest.xml");
                isShizukuAvailable = false;
            } catch (RuntimeException e) {
                Log.e(TAG, "❌ Shizuku RuntimeException: " + e.getMessage(), e);
                Log.e(TAG, "   Exception type: " + e.getClass().getSimpleName());
                isShizukuAvailable = false;
            } catch (Exception e) {
                Log.e(TAG, "❌ Unexpected exception when pinging Shizuku: " + e.getMessage(), e);
                Log.e(TAG, "   Exception type: " + e.getClass().getSimpleName());
                isShizukuAvailable = false;
            }
        }
        return isShizukuAvailable;
    }

    /**
     * Shizuku 앱이 설치되어 있는지 확인
     */
    private boolean isShizukuInstalled() {
        try {
            context.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Shizuku 권한 확인
     */
    public boolean hasShizukuPermission() {
        if (!isShizukuAvailable()) {
            Log.d(TAG, "Cannot check Shizuku permission: service not available");
            return false;
        }
        
        try {
            // Shizuku API v11 이전 버전은 지원하지 않음
            if (Shizuku.isPreV11()) {
                Log.w(TAG, "❌ Shizuku pre-v11 is not supported");
                return false;
            }
            
            Log.d(TAG, "Checking Shizuku permission...");
            int permission = Shizuku.checkSelfPermission();
            boolean hasPermission = permission == PackageManager.PERMISSION_GRANTED;
            
            Log.i(TAG, String.format("🔑 Shizuku permission check result: %s (code: %d)", 
                hasPermission ? "GRANTED ✅" : "DENIED ❌", permission));
                
            if (!hasPermission) {
                Log.w(TAG, "Permission codes: GRANTED=0, DENIED=-1, others may indicate API issues");
                
                // shouldShowRequestPermissionRationale 확인
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Log.w(TAG, "User previously denied permission and selected 'Don't ask again'");
                } else {
                    Log.i(TAG, "Can request permission normally");
                }
            }
            
            return hasPermission;
        } catch (IllegalStateException e) {
            Log.e(TAG, "❌ IllegalStateException when checking Shizuku permission: " + e.getMessage(), e);
            return false;
        } catch (RuntimeException e) {
            Log.e(TAG, "❌ RuntimeException when checking Shizuku permission: " + e.getMessage(), e);
            Log.e(TAG, "Exception type: " + e.getClass().getSimpleName());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "❌ Unexpected exception when checking Shizuku permission: " + e.getMessage(), e);
            Log.e(TAG, "Exception type: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Shizuku 권한 요청
     */
    public void requestShizukuPermission() {
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Cannot request Shizuku permission: service not available");
            return;
        }
        
        try {
            // Shizuku API v11 이전 버전은 지원하지 않음
            if (Shizuku.isPreV11()) {
                Log.w(TAG, "❌ Shizuku pre-v11 is not supported");
                return;
            }
            
            // 이미 권한이 있는지 확인
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "✅ Shizuku permission already granted");
                return;
            }
            
            // shouldShowRequestPermissionRationale 확인
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.w(TAG, "❌ User previously denied permission and selected 'Don't ask again'");
                Log.w(TAG, "   Please go to Shizuku app and manually grant permission");
                return;
            }
            
            Log.i(TAG, "📋 Requesting Shizuku permission...");
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to request Shizuku permission: " + e.getMessage(), e);
        }
    }

    /**
     * Shizuku 상태 새로고침

    /**
     * Shizuku가 올바르게 작동하는지 종합적으로 확인
     */
    public boolean isShizukuReady() {
        return isShizukuAvailable() && hasShizukuPermission();
    }

    /**
     * Shizuku를 통한 앱 강제 종료
     * @param packageName 종료할 앱의 패키지명
     * @return 성공 여부
     */
    public boolean hibernateApp(String packageName) {
        if (!isShizukuActivated()) {
            Log.w(TAG, "Shizuku mode is not activated");
            return false;
        }
        
        if (!isShizukuReady()) {
            Log.w(TAG, "Shizuku is not ready (service not available or permission not granted)");
            return false;
        }

        try {
            Log.d(TAG, "Attempting to hibernate app: " + packageName);
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

            // 먼저 단일 매개변수 메서드 시도
            try {
                Method forceStopMethod = activityManager.getClass().getMethod("forceStopPackage", String.class);
                forceStopMethod.invoke(activityManager, packageName);
                Log.i(TAG, "Successfully hibernated app (single param): " + packageName);
                return true;
            } catch (NoSuchMethodException e) {
                Log.d(TAG, "Single parameter method not found, trying with user ID");
            }
            
            // 사용자 ID 포함 메서드 시도 (Android 8.0+)
            try {
                Method forceStopMethodWithUser = activityManager.getClass().getMethod("forceStopPackage", String.class, int.class);
                int currentUserId = android.os.Process.myUserHandle().hashCode(); // 근사치
                forceStopMethodWithUser.invoke(activityManager, packageName, currentUserId);
                Log.i(TAG, "Successfully hibernated app (with user ID): " + packageName);
                return true;
            } catch (NoSuchMethodException e) {
                Log.e(TAG, "Neither forceStopPackage method variant found");
                return false;
            }
            
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
        Log.d(TAG, "Refreshing Shizuku status");
        isShizukuAvailable = null;
        // 다시 확인하여 캐시 갱신
        boolean available = isShizukuAvailable();
        Log.d(TAG, "Shizuku status refreshed: available=" + available);
    }
}
