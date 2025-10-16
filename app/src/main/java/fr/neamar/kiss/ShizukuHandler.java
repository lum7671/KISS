

    // ...existing code...


package fr.neamar.kiss;

    import android.content.Context;
    import android.content.SharedPreferences;
    import android.content.pm.PackageManager;
    import android.util.Log;

    import androidx.preference.PreferenceManager;

    import java.lang.reflect.Method;

    import rikka.shizuku.Shizuku;
    import rikka.shizuku.SystemServiceHelper;

/**
 * Shizuku를 활용한 시스템 레벨 작업 처리 클래스
 * 루트 권한 없이도 앱 강제 종료 등의 기능을 제공
 */
public class ShizukuHandler {

    /**
     * @return null이면 성공, 아니면 실패 사유(메시지)
     */
    public String hibernateAppWithReason(String packageName) {
        if (!isShizukuActivated()) {
            Log.w(TAG, "Shizuku mode is not activated");
            return "Shizuku 모드가 활성화되어 있지 않습니다.";
        }
        if (!isShizukuAvailable()) {
            Log.w(TAG, "Shizuku is not available");
            return "Shizuku 서비스가 실행 중이 아닙니다.";
        }
        if (!hasShizukuPermission()) {
            Log.w(TAG, "Shizuku permission not granted");
            return "Shizuku 권한이 없습니다.";
        }
        try {
            Log.d(TAG, "Attempting to hibernate app: " + packageName);
            boolean ok = forceStopPackage(packageName);
            if (ok) return null;
            else return "시스템 서비스 접근 실패 (forceStopPackage)";
        } catch (Exception e) {
            Log.e(TAG, "Failed to hibernate app: " + packageName, e);
            return "Shizuku를 통한 앱 종료 중 예외 발생";
        }
    }

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
    private boolean forceStopPackage(String packageName) {
        // 간단한 공식 패턴: 1) 바인더 획득 2) IActivityManager 인터페이스 획득(가능하면 Stub.asInterface, 없으면 Stub$Proxy 생성자) 3) forceStopPackage 호출
        try {
            // Use the official Shizuku pattern per documentation:
            // prefer Shizuku.getSystemService("activity"). If that fails, fall back to
            // SystemServiceHelper.getSystemService("activity"). Avoid calling
            // non-existent overloads like SystemServiceHelper.getSystemService(name, ShizukuBinderWrapper.get()).
            android.os.IBinder binder = null;
            try {
                Object svc = null;
                try {
                    // Shizuku.getSystemService(...) is not available in this API version.
                    // Use SystemServiceHelper which is provided by the Shizuku API dependency.
                    svc = SystemServiceHelper.getSystemService("activity");
                } catch (Throwable t) {
                    Log.w(TAG, "SystemServiceHelper.getSystemService failed", t);
                    svc = null;
                }

                if (svc == null) {
                    Log.e(TAG, "Failed to obtain activity service via Shizuku/SystemServiceHelper");
                    return false;
                }

                if (svc instanceof android.os.IBinder) {
                    binder = (android.os.IBinder) svc;
                } else {
                    // Rare: svc not an IBinder. Try to extract/wrap if possible.
                    try {
                        android.os.IBinder rawBinder = svc instanceof android.os.IBinder ? (android.os.IBinder) svc : null;
                        if (rawBinder != null) {
                            binder = rawBinder;
                        } else {
                            Log.w(TAG, "Activity service returned non-IBinder: " + (svc != null ? svc.getClass().getName() : "null"));
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Unexpected exception while handling activity service result", t);
                    }
                }

                if (binder == null) {
                    Log.e(TAG, "No binder available for activity service");
                    return false;
                }
            } catch (Throwable outer) {
                Log.e(TAG, "Unexpected failure while obtaining activity binder via Shizuku/SystemServiceHelper", outer);
                return false;
            }
            Object am = resolveIActivityManagerFromBinder(binder);
            if (am == null) {
                Log.e(TAG, "Failed to resolve IActivityManager from binder");
                // fallback: try shell command via Shizuku
                Log.i(TAG, "Falling back to shell 'am force-stop' via Shizuku");
                try {
                    String cmd = "am force-stop " + packageName;
                    // Use Shizuku to execute shell command
                    String output = null;
                    try {
                        // Shizuku API provides exec method in provider package; use reflection to avoid direct dependency issues
                        Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
                        Method execMethod = null;
                        try {
                            execMethod = shizukuClass.getMethod("exec", String[].class);
                        } catch (NoSuchMethodException nsme) {
                            // fallback to exec(String) or other signatures
                            try {
                                execMethod = shizukuClass.getMethod("exec", String.class);
                            } catch (NoSuchMethodException nsme2) {
                                execMethod = null;
                            }
                        }

                        if (execMethod != null) {
                            execMethod.setAccessible(true);
                            if (execMethod.getParameterTypes()[0].equals(String[].class)) {
                                Object res = execMethod.invoke(null, (Object) new String[]{"sh", "-c", cmd});
                                output = res != null ? res.toString() : null;
                            } else {
                                Object res = execMethod.invoke(null, cmd);
                                output = res != null ? res.toString() : null;
                            }
                        } else {
                            Log.w(TAG, "No Shizuku.exec method found to run shell command");
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Failed to exec shell via Shizuku reflection", t);
                    }

                    Log.i(TAG, "Shell fallback output: " + output);
                    return true;
                } catch (Throwable tb) {
                    Log.e(TAG, "Shell fallback also failed", tb);
                    return false;
                }
            }
            int userId = android.os.Process.myUid() / 100000;
            Method forceStop = am.getClass().getMethod("forceStopPackage", String.class, int.class, int.class);
            forceStop.invoke(am, packageName, userId, 0);
            Log.i(TAG, "Successfully hibernated app via Shizuku: " + packageName);
            return true;
        } catch (NoSuchMethodException nsme) {
            Log.e(TAG, "forceStopPackage method missing on IActivityManager instance", nsme);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to force stop package via Shizuku: " + packageName, e);
            return false;
        }
    }

    /**
     * Helper: try to obtain IActivityManager instance from a service binder.
     * Tries Stub.asInterface(IBinder) first; if missing, tries new Stub$Proxy(IBinder).
     */
    private Object resolveIActivityManagerFromBinder(android.os.IBinder binder) {
        try {
            Class<?> stubClass = Class.forName("android.app.IActivityManager$Stub");
            // try static asInterface
            try {
                Method asInterface = stubClass.getMethod("asInterface", android.os.IBinder.class);
                return asInterface.invoke(null, binder);
            } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException ex) {
                // fallback to Proxy constructor
                try {
                    Class<?> proxyClass = Class.forName("android.app.IActivityManager$Stub$Proxy");
                    java.lang.reflect.Constructor<?> ctor = proxyClass.getDeclaredConstructor(android.os.IBinder.class);
                    ctor.setAccessible(true);
                    return ctor.newInstance(binder);
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to instantiate Stub$Proxy", t);
                }
            }
        } catch (ClassNotFoundException cnfe) {
            Log.w(TAG, "IActivityManager$Stub class not found", cnfe);
        } catch (Throwable t) {
            Log.w(TAG, "Unexpected error resolving IActivityManager", t);
        }
        return null;
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
