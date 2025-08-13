package fr.neamar.kiss.profiling;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import androidx.tracing.Trace;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Profile 모드 전용 성능 프로파일러
 * 메모리, CPU, I/O 등의 성능 지표를 로그 파일로 저장
 */
public class PerformanceProfiler {
    private static final String TAG = "PerformanceProfiler";
    private static final String LOG_DIR_NAME = "kiss_profile_logs";
    private static final long PROFILE_INTERVAL_MS = 5000; // 5초마다 프로파일링
    
    private static PerformanceProfiler instance;
    private final Context context;
    private final ScheduledExecutorService scheduler;
    private final File logDirectory;
    private final SimpleDateFormat dateFormat;
    private FileWriter logWriter;
    private boolean isProfilingActive = false;
    
    // 성능 메트릭 추적용
    private long startTime;
    private long lastGcTime = 0;
    private Runtime runtime;
    private ActivityManager activityManager;
    
    private PerformanceProfiler(Context context) {
        this.context = context.getApplicationContext();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        this.runtime = Runtime.getRuntime();
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        
        // 안드로이드 외부 저장소에 로그 디렉토리 생성
        // /storage/emulated/0/Android/data/{package}/files/kiss_profile_logs/
        this.logDirectory = new File(context.getExternalFilesDir(null), LOG_DIR_NAME);
        if (!logDirectory.exists()) {
            boolean created = logDirectory.mkdirs();
            Log.i(TAG, "Log directory created: " + created + " at " + logDirectory.getAbsolutePath());
        }
    }
    
    public static synchronized PerformanceProfiler getInstance(Context context) {
        if (instance == null) {
            instance = new PerformanceProfiler(context);
        }
        return instance;
    }
    
    /**
     * 프로파일링 시작
     */
    public void startProfiling() {
        if (isProfilingActive) {
            Log.w(TAG, "Profiling already active");
            return;
        }
        
        try {
            String timestamp = dateFormat.format(new Date());
            File logFile = new File(logDirectory, "performance_" + timestamp + ".csv");
            logWriter = new FileWriter(logFile, true);
            
            // CSV 헤더 작성
            logWriter.write("timestamp,uptime_ms,heap_used_mb,heap_max_mb,native_heap_mb," +
                          "cpu_usage_percent,gc_count,thread_count,memory_class_mb," +
                          "large_memory_class_mb,available_memory_mb,total_memory_mb," +
                          "is_low_memory,app_startup_time_ms,method_trace_info\n");
            logWriter.flush();
            
            startTime = SystemClock.elapsedRealtime();
            isProfilingActive = true;
            
            // 주기적으로 성능 데이터 수집
            scheduler.scheduleAtFixedRate(this::collectPerformanceData, 
                                        0, PROFILE_INTERVAL_MS, TimeUnit.MILLISECONDS);
            
            Log.i(TAG, "Performance profiling started. Log file: " + logFile.getAbsolutePath());
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to start profiling", e);
        }
    }
    
    /**
     * 프로파일링 중지
     */
    public void stopProfiling() {
        if (!isProfilingActive) {
            return;
        }
        
        isProfilingActive = false;
        scheduler.shutdown();
        
        try {
            if (logWriter != null) {
                logWriter.close();
                logWriter = null;
            }
            Log.i(TAG, "Performance profiling stopped");
        } catch (IOException e) {
            Log.e(TAG, "Error closing log file", e);
        }
    }
    
    /**
     * 성능 데이터 수집 및 로깅
     */
    private void collectPerformanceData() {
        try {
            Trace.beginSection("PerformanceProfiler.collectData");
            
            long currentTime = System.currentTimeMillis();
            long uptime = SystemClock.elapsedRealtime();
            
            // 메모리 정보 수집
            Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memoryInfo);
            
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long heapMax = runtime.maxMemory();
            long nativeHeap = memoryInfo.nativePss * 1024L; // KB to bytes
            
            // 시스템 메모리 정보
            ActivityManager.MemoryInfo sysMemInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(sysMemInfo);
            
            // 스레드 수 계산
            int threadCount = Thread.activeCount();
            
            // GC 정보 (간접적으로 추정)
            long currentGcTime = Debug.threadCpuTimeNanos();
            long gcTimeDiff = currentGcTime - lastGcTime;
            lastGcTime = currentGcTime;
            
            // CPU 사용률 추정 (프로세스 CPU 시간 기반)
            double cpuUsage = calculateCpuUsage();
            
            // 앱 시작 시간
            long appStartupTime = uptime - startTime;
            
            // 메서드 추적 정보
            String methodTraceInfo = getMethodTraceInfo();
            
            // CSV 형태로 로깅
            String logEntry = String.format(Locale.US,
                "%d,%d,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%d,%.2f,%.2f,%b,%d,%s\n",
                currentTime, uptime,
                heapUsed / (1024.0 * 1024.0),      // MB
                heapMax / (1024.0 * 1024.0),       // MB  
                nativeHeap / (1024.0 * 1024.0),    // MB
                cpuUsage,                           // %
                gcTimeDiff / 1000000,               // GC time in ms
                threadCount,
                activityManager.getMemoryClass(),
                activityManager.getLargeMemoryClass(),
                sysMemInfo.availMem / (1024.0 * 1024.0),  // MB
                sysMemInfo.totalMem / (1024.0 * 1024.0),  // MB
                sysMemInfo.lowMemory,
                appStartupTime,
                methodTraceInfo
            );
            
            if (logWriter != null) {
                logWriter.write(logEntry);
                logWriter.flush();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error collecting performance data", e);
        } finally {
            Trace.endSection();
        }
    }
    
    /**
     * CPU 사용률 계산 (대략적)
     */
    private double calculateCpuUsage() {
        try {
            long cpuTime = Debug.threadCpuTimeNanos();
            long wallTime = SystemClock.elapsedRealtimeNanos();
            
            // 단순한 CPU 사용률 추정
            return Math.min(100.0, (cpuTime / (double) wallTime) * 100.0);
        } catch (Exception e) {
            return 0.0;
        }
    }
    
    /**
     * 메서드 추적 정보 수집
     */
    private String getMethodTraceInfo() {
        try {
            // 현재 스택 트레이스의 깊이 정보
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            return "stack_depth:" + stack.length;
        } catch (Exception e) {
            return "trace_error";
        }
    }
    
    /**
     * 커스텀 이벤트 로깅
     */
    public void logCustomEvent(String eventName, String details) {
        if (!isProfilingActive || logWriter == null) {
            return;
        }
        
        try {
            String customLog = String.format(Locale.US,
                "%d,CUSTOM_EVENT,%s,%s,,,,,,,,,,,,%s\n",
                System.currentTimeMillis(),
                eventName,
                details,
                "custom_event"
            );
            logWriter.write(customLog);
            logWriter.flush();
            
            Log.d(TAG, "Custom event logged: " + eventName + " - " + details);
        } catch (IOException e) {
            Log.e(TAG, "Error logging custom event", e);
        }
    }
    
    /**
     * 로그 디렉토리 경로 반환
     */
    public String getLogDirectoryPath() {
        return logDirectory.getAbsolutePath();
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        stopProfiling();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
}
