package fr.neamar.kiss;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.LinkedHashMap;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.db.AppRecord;
import fr.neamar.kiss.db.DBHelper;
import fr.neamar.kiss.icons.IconPack;
import fr.neamar.kiss.icons.IconPackXML;
import fr.neamar.kiss.icons.SystemIconPack;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.result.AppResult;
import fr.neamar.kiss.result.TagDummyResult;
import fr.neamar.kiss.utils.DrawableUtils;
import fr.neamar.kiss.utils.IconShape;
import fr.neamar.kiss.utils.IconCacheManager;
import fr.neamar.kiss.utils.PackageManagerUtils;
import fr.neamar.kiss.utils.UserHandle;
import fr.neamar.kiss.utils.CoroutineUtils;
import kotlinx.coroutines.Job;

/**
 * Inspired from <a href="http://stackoverflow.com/questions/31490630/how-to-load-icon-from-icon-pack">How to load icon from icon pack</a>
 */

public class IconsHandler {

    private static final String TAG = IconsHandler.class.getSimpleName();
    // map with available icons packs
    private final HashMap<String, String> iconsPacks = new HashMap<>();

    private final PackageManager pm;
    private final Context ctx;
    private IconPackXML mIconPack = null;
    private final SystemIconPack mSystemPack = new SystemIconPack();
    private boolean mForceAdaptive = false;
    private boolean mContactPackMask = false;
    private IconShape mContactsShape = IconShape.SHAPE_SYSTEM;
    private boolean mForceShape = false;
    private Job mLoadIconsPackTask = null;
    private volatile Map<String, Long> customIconIds = null;
    
        // 아이콘 성능 최적화를 위한 필드들
    private IconCacheManager iconCacheManager;
    private volatile boolean isScreenOn = true;
    private long lastCacheCleanTime = 0;
    private final AtomicLong accessCounter = new AtomicLong(0);
    
    /**
     * LRU 기반 아이콘 캐시 구현 (LinkedHashMap 상속 대신 조합 사용)
     */
    private static class LruIconCache {
        private final LinkedHashMap<String, Drawable> cache;
        private final int maxSize;
        private final AtomicLong hitCount = new AtomicLong(0);
        private final AtomicLong missCount = new AtomicLong(0);
        
        LruIconCache(int maxSize) {
            this.maxSize = maxSize;
            this.cache = new LinkedHashMap<String, Drawable>(maxSize + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Drawable> eldest) {
                    boolean shouldRemove = size() > LruIconCache.this.maxSize;
                    if (shouldRemove && BuildConfig.DEBUG) {
                        Log.d(TAG, "LRU evicting icon: " + eldest.getKey());
                    }
                    return shouldRemove;
                }
            };
        }
        
        public synchronized Drawable get(String key) {
            Drawable value = cache.get(key);
            if (value != null) {
                hitCount.incrementAndGet();
            } else {
                missCount.incrementAndGet();
            }
            return value;
        }
        
        public synchronized Drawable put(String key, Drawable value) {
            return cache.put(key, value);
        }
        
        public synchronized int size() {
            return cache.size();
        }
        
        public synchronized void clear() {
            cache.clear();
        }
        
        public synchronized void logStats() {
            long hits = hitCount.get();
            long misses = missCount.get();
            long total = hits + misses;
            if (total > 0) {
                Log.i(TAG, String.format("Icon Cache Stats - Size: %d/%d, Hit Rate: %.1f%% (%d/%d)", 
                    size(), maxSize, (hits * 100.0f / total), hits, total));
            }
        }
        
        public synchronized void clearStats() {
            hitCount.set(0);
            missCount.set(0);
        }
    }

    public IconsHandler(Context ctx) {
        super();
        this.ctx = ctx;
        this.pm = ctx.getPackageManager();
        
        // 고성능 아이콘 캐시 매니저 초기화
        this.iconCacheManager = IconCacheManager.getInstance(ctx);
        
        clearOldCache();
        loadAvailableIconsPacks();
        loadIconsPack();
    }

    /**
     * Load configured icons pack
     */
    private void loadIconsPack() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        onPrefChanged(prefs, "icons-pack");
    }

    /**
     * Set values from preferences
     */
    public void onPrefChanged(SharedPreferences pref, String key) {
        if (key.equalsIgnoreCase("icons-pack") ||
                key.equalsIgnoreCase("adaptive-shape") ||
                key.equalsIgnoreCase("force-adaptive") ||
                key.equalsIgnoreCase("force-shape") ||
                key.equalsIgnoreCase("contact-pack-mask") ||
                key.equalsIgnoreCase("contacts-shape") ||
                key.equalsIgnoreCase(DrawableUtils.KEY_THEMED_ICONS)) {
            cacheClear();
            mSystemPack.setAdaptiveShape(getAdaptiveShape(pref, "adaptive-shape"));
            mForceAdaptive = pref.getBoolean("force-adaptive", true);
            mForceShape = pref.getBoolean("force-shape", true);
            mContactPackMask = pref.getBoolean("contact-pack-mask", true);
            mContactsShape = getAdaptiveShape(pref, "contacts-shape");
            loadIconsPack(pref.getString("icons-pack", null));
        }
    }

    @NonNull
    private static IconShape getAdaptiveShape(SharedPreferences pref, String key) {
        try {
            int shapeId = Integer.parseInt(pref.getString(key, String.valueOf(IconShape.SHAPE_SYSTEM.getId())));
            return IconShape.valueById(shapeId);
        } catch (Exception e) {
            return IconShape.SHAPE_SYSTEM;
        }
    }

    /**
     * Parse icons pack metadata
     *
     * @param packageName Android package ID of the package to parse
     */
    private void loadIconsPack(String packageName) {
        // system icons, nothing to do
        if (packageName == null || packageName.equalsIgnoreCase("default")) {
            cacheClear();
            mIconPack = null;
            return;
        }

        // don't reload the icon pack
        if (mIconPack == null || !mIconPack.getPackPackageName().equals(packageName)) {
            cacheClear();
            if (mLoadIconsPackTask != null)
                mLoadIconsPackTask.cancel(null);
            final IconPackXML iconPack = KissApplication.iconPackCache(ctx).getIconPack(packageName);
            // set the current icon pack
            mIconPack = iconPack;
            // start async loading
            mLoadIconsPackTask = CoroutineUtils.runAsync(() -> {
                iconPack.load(ctx.getPackageManager());
            }, () -> {
                mLoadIconsPackTask = null;
            });
        }
    }

    /**
     * Get or generate icon for an app.
     * Uses cache and allow custom icons only if icon pack is in use.
     *
     * @param componentName component name
     * @param userHandle    user handle
     * @return drawable
     */
    public Drawable getDrawableIconForPackage(ComponentName componentName, UserHandle userHandle) {
        return getDrawableIconForPackage(componentName, userHandle, true, mIconPack != null);
    }

    /**
     * Get or generate icon for an app.
     *
     * @param componentName  component name
     * @param userHandle     user handle
     * @param useCache       use icon cache
     * @param useCustomIcons use custom icons
     * @return drawable
     */
    public Drawable getDrawableIconForPackage(ComponentName componentName, UserHandle userHandle, boolean useCache, boolean useCustomIcons) {
        final String cacheKey = AppPojo.getComponentName(componentName.getPackageName(), componentName.getClassName(), userHandle);

        // IconCacheManager를 통한 스마트 캐싱
        if (useCache) {
            Drawable cachedIcon = iconCacheManager.getIcon(cacheKey);
            if (cachedIcon != null) {
                return cachedIcon;
            }
        }

        // 실제 아이콘 생성
        Drawable drawable = loadIconWithFallback(componentName, userHandle, cacheKey, useCache, useCustomIcons);
        
        // IconCacheManager에 저장
        if (drawable != null && useCache) {
            iconCacheManager.putIcon(cacheKey, drawable);
        }
        
        return drawable;
    }
    
    /**
     * 폴백과 함께 아이콘 로딩 (성능 최적화)
     */
    private Drawable loadIconWithFallback(ComponentName componentName, UserHandle userHandle, 
                                        String cacheKey, boolean useCache, boolean useCustomIcons) {
        Drawable drawable = null;

        // search for custom icon
        if (useCustomIcons) {
            Map<String, Long> customIconIds = getCustomIconIds();
            if (customIconIds != null) {
                Long customIconId = customIconIds.get(cacheKey);
                if (customIconId != null) {
                    drawable = getCustomIcon(cacheKey, customIconId);
                }
            }
        }

        // check the icon pack for a resource
        if (drawable == null && mIconPack != null) {
            // 아이콘팩 로딩 중이면 시스템 아이콘 우선 반환 (번뜩임 방지)
            if (!mIconPack.isLoaded()) {
                drawable = mSystemPack.getComponentDrawable(ctx, componentName, userHandle);
                if (drawable != null) {
                    drawable = applyIconMask(ctx, drawable, false);
                }
            } else {
                drawable = mIconPack.getComponentDrawable(ctx, componentName, userHandle);
                if (drawable != null) {
                    drawable = applyIconMask(ctx, drawable, true);
                }
            }
        }

        if (drawable == null) {
            // if icon pack doesn't have the drawable, use system drawable
            drawable = mSystemPack.getComponentDrawable(ctx, componentName, userHandle);
            if (drawable != null) {
                drawable = applyIconMask(ctx, drawable, false);
            }
        }
        
        if (drawable == null) {
            return null;
        }

        drawable = applyBadge(drawable, userHandle);
        return drawable;
    }
    
    /**
     * 화면 상태 업데이트 (MainActivity에서 호출)
     */
    public void onScreenStateChanged(boolean screenOn) {
        isScreenOn = screenOn;
        
        if (!screenOn) {
            // 화면이 꺼지면 IconCacheManager를 통한 메모리 정리
            iconCacheManager.trimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
        }
    }

    public Drawable getBackgroundDrawable(@ColorInt int backgroundColor) {
        // just checking will make this thread wait for the icon pack to load
        if (mIconPack != null && !mIconPack.isLoaded()) {
            return null;
        }

        final IconShape shape = getShapeForGeneratingDrawable();
        Drawable drawable = DrawableUtils.generateBackgroundDrawable(ctx, backgroundColor, shape);
        return forceIconMask(drawable, shape);
    }

    public Drawable getDrawableIconForCodepoint(int codePoint, @ColorInt int textColor, @ColorInt int backgroundColor) {
        // 태그 아이콘 캐시 키 생성
        String tagCacheKey = String.format("tag_%d_%x_%x", codePoint, textColor, backgroundColor);
        
        // 캐시에서 먼저 확인
        Drawable cachedIcon = iconCacheManager.getIcon(tagCacheKey);
        if (cachedIcon != null) {
            return cachedIcon;
        }
        
        // just checking will make this thread wait for the icon pack to load
        if (mIconPack != null && !mIconPack.isLoaded()) {
            return null;
        }
        final IconShape shape = getShapeForGeneratingDrawable();
        Drawable drawable = DrawableUtils.generateCodepointDrawable(ctx, codePoint, textColor, backgroundColor, shape);
        drawable = forceIconMask(drawable, shape);
        
        // 생성된 태그 아이콘을 캐시에 저장
        if (drawable != null) {
            iconCacheManager.putIcon(tagCacheKey, drawable);
        }
        
        return drawable;
    }

    public Drawable applyIconMask(@NonNull Context ctx, @NonNull Drawable drawable) {
        return applyIconMask(ctx, drawable, false);
    }

    private Drawable applyIconMask(@NonNull Context ctx, @NonNull Drawable drawable, boolean isIconFromPack) {
        if (mIconPack != null && mIconPack.hasMask()) {
            if (isIconFromPack) {
                // use drawable from icon pack as is
                return drawable;
            } else {
                // if the icon pack has a mask, use that instead of the adaptive shape
                return mIconPack.applyBackgroundAndMask(ctx, drawable, false, Color.TRANSPARENT);
            }
        } else if (DrawableUtils.isAdaptiveIconDrawable(drawable) || mForceAdaptive) {
            // use adaptive shape (with white background for non adaptive icons)
            return mSystemPack.applyBackgroundAndMask(ctx, drawable, true, Color.WHITE);
        } else if (mForceShape) {
            // use adaptive shape
            return mSystemPack.applyBackgroundAndMask(ctx, drawable, false, Color.TRANSPARENT);
        } else {
            return drawable;
        }
    }

    public Drawable applyBadge(@NonNull Drawable drawable, @NonNull UserHandle userHandle) {
        Drawable badgedDrawable = pm.getUserBadgedIcon(drawable, userHandle.getRealHandle());
        if (badgedDrawable != null) {
            return badgedDrawable;
        }
        return drawable;
    }

    public Drawable applyContactMask(@NonNull Context ctx, @NonNull Drawable drawable) {
        final IconShape shape = getContactsShape();

        if (mContactPackMask && mIconPack != null && mIconPack.hasMask()) {
            // if the icon pack has a mask, use that instead of the adaptive shape
            return mIconPack.applyBackgroundAndMask(ctx, drawable, false, Color.TRANSPARENT);
        } else if (DrawableUtils.isAdaptiveIconDrawable(drawable)) {
            // use adaptive shape (with white background for non adaptive icons)
            return DrawableUtils.applyIconMaskShape(ctx, drawable, shape, true, Color.WHITE);
        } else {
            // use adaptive shape
            return DrawableUtils.applyIconMaskShape(ctx, drawable, shape, false, Color.TRANSPARENT);
        }
    }

    /**
     * Force icon mask to be applied to given drawable.
     *
     * @param drawable drawable to mask
     * @return masked drawable
     */
    private Drawable forceIconMask(@NonNull Drawable drawable, @NonNull IconShape shape) {
        // apply mask
        if (mIconPack != null && mIconPack.hasMask()) {
            // if the icon pack has a mask, use that instead of the adaptive shape
            return mIconPack.applyBackgroundAndMask(ctx, drawable, false, Color.TRANSPARENT);
        } else {
            // use adaptive shape
            return DrawableUtils.applyIconMaskShape(ctx, drawable, shape, false, Color.TRANSPARENT);
        }
    }

    /**
     * Get shape used for contact icons with fallbacks.
     * If contacts shape is {@link IconShape#SHAPE_SYSTEM} app shape is used.
     * If app shape is {@link IconShape#SHAPE_SYSTEM} too and no icon mask can be configured for device, used shape is a circle.
     *
     * @return shape
     */
    @NonNull
    private IconShape getContactsShape() {
        IconShape shape = mContactsShape;
        if (shape == IconShape.SHAPE_SYSTEM) {
            shape = mSystemPack.getAdaptiveShape();
        }
        // contacts have square images, so fallback to circle explicitly
        if (shape == IconShape.SHAPE_SYSTEM && !DrawableUtils.hasDeviceConfiguredMask()) {
            shape = IconShape.SHAPE_CIRCLE;
        }
        return shape;
    }

    /**
     * Get shape used for generating drawables with fallbacks.
     * If icon pack has mask then {@link IconShape#SHAPE_SYSTEM} is used.
     * If shape is {@link IconShape#SHAPE_SYSTEM} too and no icon mask can be configured for device, used shape is a circle.
     *
     * @return shape
     */
    @NonNull
    private IconShape getShapeForGeneratingDrawable() {
        IconShape shape = mSystemPack.getAdaptiveShape();
        if (mIconPack != null && mIconPack.hasMask()) {
            shape = IconShape.SHAPE_SYSTEM;
        }
        return shape;
    }


    /**
     * Scan for installed icons packs
     */
    private void loadAvailableIconsPacks() {

        List<ResolveInfo> launcherThemes = pm.queryIntentActivities(new Intent("fr.neamar.kiss.THEMES"), PackageManager.GET_META_DATA);
        List<ResolveInfo> adwLauncherThemes = pm.queryIntentActivities(new Intent("org.adw.launcher.THEMES"), PackageManager.GET_META_DATA);

        launcherThemes.addAll(adwLauncherThemes);

        for (ResolveInfo ri : launcherThemes) {
            String packageName = ri.activityInfo.packageName;
            String name = PackageManagerUtils.getLabel(ctx, packageName, new UserHandle());
            if (name != null) {
                iconsPacks.put(packageName, name);
            } else {
                Log.e(TAG, "Unable to find package " + packageName);
            }
        }
    }

    Map<String, String> getIconsPacks() {
        return iconsPacks;
    }

    @Nullable
    public IconPackXML getCustomIconPack() {
        return mIconPack;
    }

    @NonNull
    public SystemIconPack getSystemIconPack() {
        return mSystemPack;
    }

    @NonNull
    public IconPack getIconPack() {
        return mIconPack != null ? mIconPack : mSystemPack;
    }

    private boolean isDrawableInCache(String key) {
        File drawableFile = cacheGetFileName(key);
        return drawableFile.isFile();
    }

    private void storeDrawable(File drawableFile, Drawable drawable) {
        // convert any drawable to bitmap that can be stored
        Bitmap bitmap = DrawableUtils.drawableToBitmap(drawable);
        if (bitmap != null) {
            try (FileOutputStream fos = new FileOutputStream(drawableFile)) {
                bitmap.compress(CompressFormat.PNG, 100, fos);
                fos.flush();
            } catch (Exception e) {
                Log.e(TAG, "Unable to store drawable in cache ", e);
            }
        }
    }

    private Drawable cacheGetDrawable(String key) {

        if (!isDrawableInCache(key)) {
            return null;
        }

        FileInputStream fis;
        try {
            fis = new FileInputStream(cacheGetFileName(key));
            BitmapDrawable drawable =
                    new BitmapDrawable(this.ctx.getResources(), BitmapFactory.decodeStream(fis));
            fis.close();
            return drawable;
        } catch (Exception e) {
            Log.e(TAG, "Unable to get drawable from cache ", e);
        }

        return null;
    }

    /**
     * create path for icons cache like this
     * {cacheDir}/icons/{icons_pack_package_name}_{key_hash}.png
     */
    private File cacheGetFileName(String key) {
        String iconsPackPackageName = getIconPack().getPackPackageName();
        return new File(getIconsCacheDir(), iconsPackPackageName + "_" + key.hashCode() + ".png");
    }

    private File getIconsCacheDir() {
        File dir = new File(this.ctx.getCacheDir(), "icons");
        if (!dir.exists() && !dir.mkdir())
            throw new IllegalStateException("failed to create path " + dir.getPath());
        return dir;
    }

    private File customIconFileName(String componentName, long customIcon) {
        return new File(getCustomIconsDir(), customIcon + "_" + componentName.hashCode() + ".png");
    }

    private File getCustomIconsDir() {
        File dir = new File(this.ctx.getCacheDir(), "custom_icons");
        if (!dir.exists() && !dir.mkdir())
            throw new IllegalStateException("failed to create path " + dir.getPath());
        return dir;
    }

    /**
     * Clear cache
     */
    private void cacheClear() {
        TagDummyResult.resetShape();
        clearCustomIconIdCache();

        File cacheDir = this.getIconsCacheDir();

        File[] fileList = cacheDir.listFiles();
        if (fileList != null) {
            for (File item : fileList) {
                if (!item.delete()) {
                    Log.w(TAG, "Failed to delete file: " + item.getAbsolutePath());
                }
            }
        }
    }

    // Before we fixed the cache path actually returning a folder, a lot of icons got dumped
    // directly in ctx.getCacheDir() so we need to clean it
    private void clearOldCache() {
        File newCacheDir = new File(this.ctx.getCacheDir(), "icons");

        if (!newCacheDir.isDirectory()) {
            File[] fileList = ctx.getCacheDir().listFiles();
            if (fileList != null) {
                int count = 0;
                for (File file : fileList) {
                    if (file.isFile())
                        count += file.delete() ? 1 : 0;
                }
                Log.i(TAG, "Removed " + count + " cache file(s) from the old path");
            }
        }
    }

    public Drawable getCustomIcon(String componentName, long customIcon) {
        if (customIcon == 0)
            return null;

        try {
            FileInputStream fis = new FileInputStream(customIconFileName(componentName, customIcon));
            BitmapDrawable drawable =
                    new BitmapDrawable(this.ctx.getResources(), BitmapFactory.decodeStream(fis));
            fis.close();
            return drawable;
        } catch (Exception e) {
            Log.e(TAG, "Unable to get custom icon for " + componentName, e);
        }

        return null;
    }

    private void removeStoredDrawable(@NonNull File drawableFile) {
        try {
            //noinspection ResultOfMethodCallIgnored
            drawableFile.delete();
        } catch (Exception e) {
            Log.e(TAG, "stored drawable " + drawableFile + " can't be deleted!", e);
        }
    }

    public void changeAppIcon(AppResult appResult, Drawable drawable) {
        long customIconId = KissApplication.getApplication(ctx).getDataHandler().setCustomAppIcon(appResult.getComponentName());
        storeDrawable(customIconFileName(appResult.getComponentName(), customIconId), drawable);
        appResult.setCustomIcon(customIconId, drawable);
        cacheClear();
    }

    public void restoreAppIcon(AppResult appResult) {
        long customIconId = KissApplication.getApplication(ctx).getDataHandler().removeCustomAppIcon(appResult.getComponentName());
        removeStoredDrawable(customIconFileName(appResult.getComponentName(), customIconId));
        appResult.clearCustomIcon();
        cacheClear();
    }

    /**
     * clears cache for custom icon ids
     */
    private void clearCustomIconIdCache() {
        synchronized (this) {
            customIconIds = null;
        }
    }

    /**
     * Thread-safe cache for custom icon ids, maps from component name to custom icon id.
     * Cache is built only if null.
     *
     * @return cache for custom icon ids
     */
    private Map<String, Long> getCustomIconIds() {
        if (customIconIds == null) {
            synchronized (this) {
                if (customIconIds == null) {
                    customIconIds = new HashMap<>();
                    Map<String, AppRecord> appData = DBHelper.getCustomAppData(ctx);
                    for (Map.Entry<String, AppRecord> entry : appData.entrySet()) {
                        if (entry.getValue().hasCustomIcon()) {
                            customIconIds.put(entry.getKey(), entry.getValue().dbId);
                        }
                    }
                }
            }
        }
        return customIconIds;
    }

}
