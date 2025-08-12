package fr.neamar.kiss;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import fr.neamar.kiss.utils.IconPackCache;

// 성능 분석 도구 imports (DEBUG 빌드에만 포함)
import com.github.anrwatchdog.ANRWatchDog;
import curtains.Curtains;

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
    
    private void initializePerformanceTools() {
        // ANR (Application Not Responding) 감지 도구
        new ANRWatchDog(5000) // 5초 응답 없으면 ANR로 간주
            .setANRListener(error -> {
                // ANR 발생 시 로그 출력
                android.util.Log.e("KISS_ANR", "ANR detected!", error);
            })
            .start();
            
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
        }
    }
}
