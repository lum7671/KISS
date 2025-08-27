package fr.neamar.kiss.loader

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInfo
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
            // Use LauncherApps for API 21+ - 활성화된 앱들
            val activityList = launcherApps.getActivityList(null, userHandle.realHandle)
            
            for (activityInfo in activityList) {
                val suspended = if (android.os.Build.VERSION.SDK_INT >= 28) {
                    (activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0
                } else {
                    false
                }
                val disabled = !activityInfo.applicationInfo.enabled
                android.util.Log.d(TAG, "LauncherApps: package=" + activityInfo.applicationInfo.packageName + ", disabled=" + disabled + ", suspended=" + suspended + ", flags=" + activityInfo.applicationInfo.flags)
                val app = createPojo(
                    userHandle,
                    activityInfo.applicationInfo.packageName,
                    activityInfo.name,
                    activityInfo.label,
                    disabled,
                    suspended,
                    excludedAppList,
                    excludedFromHistoryAppList,
                    excludedShortcutsAppList
                )
                apps.add(app)
            }
            
            // 비활성화된 앱 로딩 기능을 임시로 비활성화 (중복 및 실행 오류 방지)
            /*
            // 추가로 비활성화된 앱들도 로드 (PackageManager 사용)
            try {
                val pm = ctx.packageManager
                
                // 이미 추가된 앱들의 패키지명을 Set으로 저장 (더 안전한 방법)
                val existingPackages = activityList.map { it.applicationInfo.packageName }.toSet()
                
                // 모든 설치된 패키지를 확인 (비활성화된 것 포함)
                val allPackages = pm.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES)
                
                for (packageInfo in allPackages) {
                    try {
                        // 이미 추가된 패키지는 스킵 (중복 방지)
                        if (existingPackages.contains(packageInfo.packageName)) {
                            continue
                        }
                        
                        // LAUNCHER 카테고리가 있는 Activity들을 찾기
                        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setPackage(packageInfo.packageName)
                        }
                        
                        val activities = pm.queryIntentActivities(mainIntent, 
                            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS)
                        
                        for (activityInfo in activities) {
                            val isDisabled = !activityInfo.activityInfo.enabled || 
                                (packageInfo.applicationInfo?.enabled == false)
                            val isSuspended = if (android.os.Build.VERSION.SDK_INT >= 28) {
                                try {
                                    val field = activityInfo.activityInfo.applicationInfo.javaClass.getDeclaredField("isSuspended")
                                    field.getBoolean(activityInfo.activityInfo.applicationInfo)
                                } catch (e: Exception) {
                                    false
                                }
                            } else false || (packageInfo.applicationInfo?.let {
                                try {
                                    val field = it.javaClass.getDeclaredField("isSuspended")
                                    field.getBoolean(it)
                                } catch (e: Exception) {
                                    false
                                }
                            } == true)
                            
                            // 비활성화된 앱이나 실행할 수 없는 앱은 스킵
                            if (isDisabled) {
                                android.util.Log.d(TAG, "Skipping disabled app: ${activityInfo.activityInfo.packageName}")
                                continue
                            }
                            
                            val app = createPojo(
                                userHandle,
                                activityInfo.activityInfo.packageName,
                                activityInfo.activityInfo.name,
                                activityInfo.loadLabel(pm),
                                isDisabled,
                                isSuspended,
                                excludedAppList,
                                excludedFromHistoryAppList,
                                excludedShortcutsAppList
                            )
                            apps.add(app)
                        }
                    } catch (e: Exception) {
                        // 개별 패키지 처리 실패 시 무시하고 계속
                        android.util.Log.w(TAG, "Error processing package ${packageInfo.packageName}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Error loading disabled apps: ${e.message}")
            }
            */
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
            val suspended = if (android.os.Build.VERSION.SDK_INT >= 28) {
                (activityInfo.activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SUSPENDED) != 0
            } else {
                false
            }
            val disabled = !activityInfo.activityInfo.enabled
            android.util.Log.d(TAG, "Legacy: package=" + activityInfo.activityInfo.packageName + ", disabled=" + disabled + ", suspended=" + suspended + ", flags=" + activityInfo.activityInfo.applicationInfo.flags)
            val app = createPojo(
                userHandle,
                activityInfo.activityInfo.packageName,
                activityInfo.activityInfo.name,
                activityInfo.loadLabel(pm),
                disabled,
                suspended,
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
        suspended: Boolean,
        excludedAppList: Set<String>,
        excludedFromHistoryAppList: Set<String>,
        excludedShortcutsAppList: Set<String>
    ): AppPojo {
        val id = userHandle.addUserSuffixToString("$pojoScheme$packageName/$activityName", '/')
        
        val isExcluded = excludedAppList.contains(AppPojo.getComponentName(packageName, activityName, userHandle))
        val isExcludedFromHistory = excludedFromHistoryAppList.contains(id)
        val isExcludedShortcuts = excludedShortcutsAppList.contains(packageName)
        
        val app = AppPojo(id, packageName, activityName, userHandle, isExcluded, isExcludedFromHistory, isExcludedShortcuts, disabled, suspended)
        app.name = label.toString()
        app.tags = tagsHandler.getTags(app.id)
        
        return app
    }
}
