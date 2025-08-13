package fr.neamar.kiss.utils.pm;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import fr.neamar.kiss.utils.UserHandle;

/**
 * 애플리케이션 정보 조회 관련 유틸리티
 */
public final class ApplicationInfoUtils {
    
    private static final String TAG = ApplicationInfoUtils.class.getSimpleName();
    
    private ApplicationInfoUtils() {
        // 유틸리티 클래스
    }
    
    /**
     * 패키지명으로 ApplicationInfo를 조회합니다.
     *
     * @param context     context
     * @param packageName 패키지명
     * @param userHandle  사용자 핸들
     * @return ApplicationInfo 또는 null
     */
    @Nullable
    public static ApplicationInfo getApplicationInfo(@NonNull Context context, 
                                                   @NonNull String packageName, 
                                                   @NonNull UserHandle userHandle) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    return launcherApps.getApplicationInfo(packageName, 0, userHandle.getRealHandle());
                }
            }
            return context.getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found: " + packageName, e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting application info for: " + packageName, e);
            return null;
        }
    }
    
    /**
     * ComponentName으로 ActivityInfo를 조회합니다.
     *
     * @param context       context
     * @param componentName 컴포넌트명
     * @param userHandle    사용자 핸들
     * @return ActivityInfo 또는 null
     */
    @Nullable
    public static ActivityInfo getActivityInfo(@NonNull Context context, 
                                             @NonNull ComponentName componentName, 
                                             @NonNull UserHandle userHandle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LauncherActivityInfo info = getLauncherActivityInfo(context, componentName, userHandle);
            if (info != null) {
                return info.getActivityInfo();
            }
        }
        
        try {
            return context.getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Activity not found: " + componentName, e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error getting activity info for: " + componentName, e);
            return null;
        }
    }
    
    /**
     * LauncherActivityInfo를 조회합니다. (Android L 이상)
     *
     * @param context       context
     * @param componentName 컴포넌트명
     * @param userHandle    사용자 핸들
     * @return LauncherActivityInfo 또는 null
     */
    @Nullable
    public static LauncherActivityInfo getLauncherActivityInfo(@NonNull Context context, 
                                                             @NonNull ComponentName componentName, 
                                                             @NonNull UserHandle userHandle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    List<LauncherActivityInfo> activities = launcherApps.getActivityList(
                        componentName.getPackageName(), userHandle.getRealHandle());
                    
                    for (LauncherActivityInfo activity : activities) {
                        if (activity.getComponentName().equals(componentName)) {
                            return activity;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting launcher activity info for: " + componentName, e);
            }
        }
        return null;
    }
    
    /**
     * 애플리케이션이 시스템 앱인지 확인합니다.
     *
     * @param applicationInfo 확인할 ApplicationInfo
     * @return 시스템 앱 여부
     */
    public static boolean isSystemApp(@Nullable ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            return false;
        }
        return (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
    
    /**
     * 애플리케이션이 업데이트된 시스템 앱인지 확인합니다.
     *
     * @param applicationInfo 확인할 ApplicationInfo
     * @return 업데이트된 시스템 앱 여부
     */
    public static boolean isUpdatedSystemApp(@Nullable ApplicationInfo applicationInfo) {
        if (applicationInfo == null) {
            return false;
        }
        return (applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }
    
    /**
     * 애플리케이션이 사용자 앱인지 확인합니다.
     *
     * @param applicationInfo 확인할 ApplicationInfo
     * @return 사용자 앱 여부
     */
    public static boolean isUserApp(@Nullable ApplicationInfo applicationInfo) {
        return !isSystemApp(applicationInfo) && !isUpdatedSystemApp(applicationInfo);
    }
    
    /**
     * 패키지가 설치되어 있는지 확인합니다.
     *
     * @param context     context
     * @param packageName 확인할 패키지명
     * @return 설치 여부
     */
    public static boolean isPackageInstalled(@NonNull Context context, @NonNull String packageName) {
        return getApplicationInfo(context, packageName, new UserHandle()) != null;
    }
}
