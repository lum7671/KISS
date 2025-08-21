package fr.neamar.kiss.loader

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.annotation.WorkerThread
import fr.neamar.kiss.BuildConfig
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.TagsHandler
import fr.neamar.kiss.db.AppRecord
import fr.neamar.kiss.db.DBHelper
import fr.neamar.kiss.pojo.AppPojo
import fr.neamar.kiss.utils.PackageManagerUtils
import fr.neamar.kiss.utils.UserHandle
import java.util.*

/**
 * Kotlin Coroutines replacement for LoadAppPojos AsyncTask
 * Loads application POJOs in background using Coroutines
 */
class LoadAppPojosCoroutine(context: Context) : LoadPojosCoroutine<AppPojo>(context, "app://") {
    
    companion object {
        private const val TAG = "LoadAppPojosCoroutine"
    }
    
    private val tagsHandler: TagsHandler by lazy {
        val ctx = contextRef.get()
        ctx?.let { KissApplication.getApplication(it).dataHandler.tagsHandler }
            ?: throw IllegalStateException("Context is null when accessing TagsHandler")
    }
    
    @WorkerThread
    override fun doInBackground(): List<AppPojo> {
        val start = System.currentTimeMillis()
        
        val apps = mutableListOf<AppPojo>()
        val ctx = contextRef.get() ?: return apps
        
        val kissApp = KissApplication.getApplication(ctx)
        val excludedAppList = kissApp.dataHandler.excluded
        val excludedFromHistoryAppList = kissApp.dataHandler.excludedFromHistory
        val excludedShortcutsAppList = kissApp.dataHandler.excludedShortcutApps
        
        // Load apps for each user profile
        loadAppsForAllProfiles(ctx, apps, excludedAppList, excludedFromHistoryAppList, excludedShortcutsAppList)
        
        // Apply custom information from database
        applyCustomAppInfo(ctx, apps)
        
        val end = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "${end - start} milliseconds to list apps")
        }
        
        return apps
    }
    
    @WorkerThread
    private fun loadAppsForAllProfiles(
        ctx: Context,
        apps: MutableList<AppPojo>,
        excludedAppList: Set<String>,
        excludedFromHistoryAppList: Set<String>,
        excludedShortcutsAppList: Set<String>
    ) {
        val manager = ctx.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        
        // Get all user profiles
        val profiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            manager.userProfiles
        } else {
            listOf(android.os.Process.myUserHandle())
        }
        
        for (profile in profiles) {
            val userHandle = UserHandle(ctx, profile)
            
            // Skip quiet mode profiles
            if (isQuietModeEnabled(manager, profile)) {
                continue
            }
            
            try {
                loadAppsForProfile(ctx, launcherApps, userHandle, apps, excludedAppList, excludedFromHistoryAppList, excludedShortcutsAppList)
            } catch (e: Exception) {
                Log.w(TAG, "Error loading apps for profile ${profile}: ${e.message}")
            }
        }
    }
    
    @WorkerThread
    private fun loadAppsForProfile(
        ctx: Context,
        launcherApps: LauncherApps,
        userHandle: UserHandle,
        apps: MutableList<AppPojo>,
        excludedAppList: Set<String>,
        excludedFromHistoryAppList: Set<String>,
        excludedShortcutsAppList: Set<String>
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Use LauncherApps for API 21+
            val activityList = launcherApps.getActivityList(null, userHandle.realHandle)
            
            for (activityInfo in activityList) {
                val app = createPojo(
                    userHandle,
                    activityInfo.applicationInfo.packageName,
                    activityInfo.name,
                    activityInfo.label,
                    !activityInfo.applicationInfo.enabled,
                    excludedAppList,
                    excludedFromHistoryAppList,
                    excludedShortcutsAppList
                )
                apps.add(app)
            }
        } else {
            // Fallback for older Android versions
            loadAppsLegacy(ctx, userHandle, apps, excludedAppList, excludedFromHistoryAppList, excludedShortcutsAppList)
        }
    }
    
    @WorkerThread
    private fun loadAppsLegacy(
        ctx: Context,
        userHandle: UserHandle,
        apps: MutableList<AppPojo>,
        excludedAppList: Set<String>,
        excludedFromHistoryAppList: Set<String>,
        excludedShortcutsAppList: Set<String>
    ) {
        val pm = ctx.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val activitiesInfo = pm.queryIntentActivities(mainIntent, 0)
        
        for (activityInfo in activitiesInfo) {
            val app = createPojo(
                userHandle,
                activityInfo.activityInfo.packageName,
                activityInfo.activityInfo.name,
                activityInfo.loadLabel(pm),
                !activityInfo.activityInfo.enabled,
                excludedAppList,
                excludedFromHistoryAppList,
                excludedShortcutsAppList
            )
            apps.add(app)
        }
    }
    
    @WorkerThread
    private fun applyCustomAppInfo(ctx: Context, apps: List<AppPojo>) {
        val customApps = DBHelper.getCustomAppData(ctx)
        
        for (app in apps) {
            val customApp = customApps[app.componentName]
            if (customApp != null) {
                if (customApp.hasCustomName()) {
                    app.setName(customApp.name)
                }
                if (customApp.hasCustomIcon()) {
                    app.setCustomIconId(customApp.dbId)
                }
            }
        }
    }
    
    private fun isQuietModeEnabled(manager: UserManager, profile: android.os.UserHandle): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.isQuietModeEnabled(profile)
        } else {
            false
        }
    }
    
    private fun createPojo(
        userHandle: UserHandle,
        packageName: String,
        activityName: String,
        label: CharSequence,
        disabled: Boolean,
        excludedAppList: Set<String>,
        excludedFromHistoryAppList: Set<String>,
        excludedShortcutsAppList: Set<String>
    ): AppPojo {
        val id = userHandle.addUserSuffixToString("$pojoScheme$packageName/$activityName", '/')
        
        val isExcluded = excludedAppList.contains(AppPojo.getComponentName(packageName, activityName, userHandle))
        val isExcludedFromHistory = excludedFromHistoryAppList.contains(id)
        val isExcludedShortcuts = excludedShortcutsAppList.contains(packageName)
        
        val app = AppPojo(id, packageName, activityName, userHandle, isExcluded, isExcludedFromHistory, isExcludedShortcuts, disabled)
        app.name = label.toString()
        app.tags = tagsHandler.getTags(app.id)
        
        return app
    }
}
