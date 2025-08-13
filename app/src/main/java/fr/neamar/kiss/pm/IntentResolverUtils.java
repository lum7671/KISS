package fr.neamar.kiss.utils.pm;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Intent 해결 관련 유틸리티
 */
public final class IntentResolverUtils {
    
    private static final String TAG = IntentResolverUtils.class.getSimpleName();
    
    private IntentResolverUtils() {
        // 유틸리티 클래스
    }
    
    /**
     * 주어진 Intent에 대한 최적의 ResolveInfo를 찾습니다.
     *
     * @param context context
     * @param intent  intent
     * @return 최적의 앱에 대한 ResolveInfo
     */
    @Nullable
    public static ResolveInfo getBestResolve(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) {
            return null;
        }

        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> matches = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        final int size = matches.size();
        if (size == 0) {
            Log.d(TAG, "No apps found for intent: " + intent);
            return null;
        } else if (size == 1) {
            return matches.get(0);
        }

        // 기본 액티비티 찾기 시도, 그렇지 않으면 disambig 감지
        final ResolveInfo foundResolve = packageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (foundResolve == null) {
            Log.w(TAG, "No resolve found for intent: " + intent);
            return matches.get(0);
        }
        
        final boolean foundDisambig = (foundResolve.match &
                IntentFilter.MATCH_CATEGORY_MASK) == 0;

        if (!foundDisambig) {
            // 구체적인 매치를 발견했으므로 직접 반환
            return foundResolve;
        }

        // 첫 번째 시스템 앱 수락
        ResolveInfo firstSystem = null;
        for (ResolveInfo info : matches) {
            final boolean isSystem = (info.activityInfo.applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) != 0;

            if (isSystem && firstSystem == null) {
                firstSystem = info;
            }
        }

        // 첫 번째 시스템을 발견하면 반환, 그렇지 않으면 목록에서 첫 번째 반환
        return firstSystem != null ? firstSystem : matches.get(0);
    }

    /**
     * 주어진 Intent에 대한 최적의 앱의 ComponentName을 반환합니다.
     *
     * @param context context
     * @param intent  intent
     * @return 최적의 앱의 ComponentName
     */
    @Nullable
    public static ComponentName getBestComponent(@NonNull Context context, @Nullable Intent intent) {
        ResolveInfo resolveInfo = getBestResolve(context, intent);
        if (resolveInfo != null && resolveInfo.activityInfo != null) {
            return new ComponentName(resolveInfo.activityInfo.packageName, 
                                   resolveInfo.activityInfo.name);
        }
        return null;
    }

    /**
     * 주어진 Intent에 대한 최적의 앱의 라벨을 반환합니다.
     *
     * @param context context
     * @param intent  intent
     * @return 최적의 앱의 라벨
     */
    @Nullable
    public static String getBestLabel(@NonNull Context context, @Nullable Intent intent) {
        ResolveInfo resolveInfo = getBestResolve(context, intent);
        if (resolveInfo != null) {
            return String.valueOf(resolveInfo.loadLabel(context.getPackageManager()));
        } else {
            Log.w(TAG, "Unable to get label from intent: " + intent);
            return null;
        }
    }
    
    /**
     * Intent에 매치되는 모든 앱을 반환합니다.
     *
     * @param context context
     * @param intent  intent
     * @return 매치되는 앱들의 ResolveInfo 목록
     */
    @NonNull
    public static List<ResolveInfo> getAllMatches(@NonNull Context context, @NonNull Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
    }
    
    /**
     * Intent가 해결 가능한지 확인합니다.
     *
     * @param context context
     * @param intent  intent
     * @return 해결 가능 여부
     */
    public static boolean isResolvable(@NonNull Context context, @Nullable Intent intent) {
        return getBestResolve(context, intent) != null;
    }
}
