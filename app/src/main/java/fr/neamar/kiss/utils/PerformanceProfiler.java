package fr.neamar.kiss.utils;

import android.util.Log;

/**
 * 성능 측정을 위한 간단한 프로파일러
 */
public class PerformanceProfiler {
    private static final String TAG = "KISS_PERF";
    
    public static class Timer {
        private final String name;
        private final long startTime;
        
        public Timer(String name) {
            this.name = name;
            this.startTime = System.nanoTime();
        }
        
        public void log() {
            long elapsed = System.nanoTime() - startTime;
            double elapsedMs = elapsed / 1_000_000.0;
            Log.d(TAG, String.format("%s: %.2fms", name, elapsedMs));
        }
    }
    
    public static Timer startTimer(String name) {
        return new Timer(name);
    }
    
    public static void logMemoryUsage(String context) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usagePercent = (usedMemory * 100.0) / maxMemory;
        
        Log.d(TAG, String.format("%s - Memory: %.1fMB/%.1fMB (%.1f%%)", 
            context, 
            usedMemory / (1024.0 * 1024.0),
            maxMemory / (1024.0 * 1024.0),
            usagePercent));
    }
}
