package fr.neamar.kiss;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import fr.neamar.kiss.utils.IconPackCache;

public class KissApplication extends Application {
    /**
     * Number of ms to wait, after a click occurred, to record a launch
     * Setting this value to 0 removes all animations
     */
    public static final int TOUCH_DELAY = 120;
    private volatile DataHandler dataHandler;
    private volatile RootHandler rootHandler;
    private volatile IconsHandler iconsPackHandler;
    private final IconPackCache mIconPackCache = new IconPackCache();
    private final MimeTypeCache mimeTypeCache = new MimeTypeCache();

    public static KissApplication getApplication(Context context) {
        return (KissApplication) context.getApplicationContext();
    }

    public static IconPackCache iconPackCache(Context ctx) {
        return getApplication(ctx).mIconPackCache;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 성능 분석 도구 초기화 (DEBUG 빌드에만)
        if (BuildConfig.DEBUG) {
            initializePerformanceTools();
        }
    }
    
    @Override
    public void onTerminate() {
        // 앱 종료 시 메모리 DB 동기화
        fr.neamar.kiss.db.DBHelper.forceSync(this);
        super.onTerminate();
    }
    
    private void initializePerformanceTools() {
        // 디버그 빌드에서만 성능 모니터링 도구 활성화
        if (BuildConfig.DEBUG) {
            try {
                // ANR (Application Not Responding) 감지 도구
                Class<?> anrWatchDogClass = Class.forName("com.github.anrwatchdog.ANRWatchDog");
                Object anrWatchDog = anrWatchDogClass.getConstructor(int.class).newInstance(5000);
                anrWatchDogClass.getMethod("start").invoke(anrWatchDog);
                android.util.Log.i("KISS_PERF", "ANR monitoring started");
            } catch (Exception e) {
                android.util.Log.w("KISS_PERF", "ANR monitoring not available", e);
            }
        }
        android.util.Log.i("KISS_PERF", "Performance monitoring tools initialized");
    }

    public DataHandler getDataHandler() {
        if (dataHandler == null) {
            synchronized (this) {
                if (dataHandler == null) {
                    dataHandler = new DataHandler(this);
                }
            }
        }
        return dataHandler;
    }

    public RootHandler getRootHandler() {
        if (rootHandler == null) {
            synchronized (this) {
                if (rootHandler == null) {
                    rootHandler = new RootHandler(this);
                }
            }
        }
        return rootHandler;
    }

    public void resetRootHandler(Context ctx) {
        rootHandler.resetRootHandler(ctx);
    }

    public void initDataHandler() {
        DataHandler dataHandler = getDataHandler();
        if (dataHandler != null && dataHandler.allProvidersHaveLoaded) {
            // Already loaded! We still need to fire the FULL_LOAD event
            Intent i = new Intent(MainActivity.FULL_LOAD_OVER);
            sendBroadcast(i);
        }
    }

    public IconsHandler getIconsHandler() {
        if (iconsPackHandler == null) {
            synchronized (this) {
                if (iconsPackHandler == null) {
                    iconsPackHandler = new IconsHandler(this);
                }
            }
        }

        return iconsPackHandler;
    }

    public void resetIconsHandler() {
        iconsPackHandler = new IconsHandler(this);
    }

    public static MimeTypeCache getMimeTypeCache(Context ctx) {
        return getApplication(ctx).mimeTypeCache;
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // this is called every time the screen is off
            SQLiteDatabase.releaseMemory();
            mIconPackCache.clearCache(this);
            mimeTypeCache.clearCache();
            
            // IconCacheManager 메모리 정리
            if (iconsPackHandler != null) {
                iconsPackHandler.onScreenStateChanged(false);
            }
        }
        
        // 메모리 부족 시 즉시 동기화 및 정리
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            fr.neamar.kiss.db.DBHelper.forceSync(this);
            
            // 아이콘 캐시 추가 정리
            fr.neamar.kiss.utils.IconCacheManager.getInstance(this).trimMemory(level);
        }
        
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
            // 심각한 메모리 부족 시 디스크 모드로 전환
            fr.neamar.kiss.db.DBHelper.switchToDiskMode(this);
        }
    }
}
