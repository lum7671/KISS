package fr.neamar.kiss.utils.pm;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * 컴포넌트 활성화/비활성화 관련 유틸리티
 */
public final class ComponentUtils {
    
    private static final String TAG = ComponentUtils.class.getSimpleName();
    
    private ComponentUtils() {
        // 유틸리티 클래스
    }
    
    /**
     * 특정 컴포넌트를 활성화/비활성화합니다.
     *
     * @param context   context
     * @param component 활성화/비활성화할 컴포넌트 클래스
     * @param enabled   활성화 여부
     */
    public static void setComponentEnabled(@NonNull Context context, 
                                         @NonNull Class<?> component, 
                                         boolean enabled) {
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName cn = new ComponentName(context, component);
            
            int newState = enabled ? 
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED : 
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                
            pm.setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP);
            
            Log.d(TAG, "Component " + component.getSimpleName() + 
                  (enabled ? " enabled" : " disabled"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set component state for " + component.getSimpleName(), e);
        }
    }
    
    /**
     * ComponentName으로 컴포넌트를 활성화/비활성화합니다.
     *
     * @param context       context
     * @param componentName 활성화/비활성화할 컴포넌트명
     * @param enabled       활성화 여부
     */
    public static void setComponentEnabled(@NonNull Context context, 
                                         @NonNull ComponentName componentName, 
                                         boolean enabled) {
        try {
            PackageManager pm = context.getPackageManager();
            
            int newState = enabled ? 
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED : 
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                
            pm.setComponentEnabledSetting(componentName, newState, PackageManager.DONT_KILL_APP);
            
            Log.d(TAG, "Component " + componentName.getClassName() + 
                  (enabled ? " enabled" : " disabled"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to set component state for " + componentName, e);
        }
    }
    
    /**
     * 컴포넌트가 활성화되어 있는지 확인합니다.
     *
     * @param context   context
     * @param component 확인할 컴포넌트 클래스
     * @return 활성화 여부
     */
    public static boolean isComponentEnabled(@NonNull Context context, @NonNull Class<?> component) {
        try {
            PackageManager pm = context.getPackageManager();
            ComponentName cn = new ComponentName(context, component);
            
            int state = pm.getComponentEnabledSetting(cn);
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                   state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get component state for " + component.getSimpleName(), e);
            return false;
        }
    }
    
    /**
     * ComponentName으로 컴포넌트가 활성화되어 있는지 확인합니다.
     *
     * @param context       context
     * @param componentName 확인할 컴포넌트명
     * @return 활성화 여부
     */
    public static boolean isComponentEnabled(@NonNull Context context, @NonNull ComponentName componentName) {
        try {
            PackageManager pm = context.getPackageManager();
            
            int state = pm.getComponentEnabledSetting(componentName);
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                   state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get component state for " + componentName, e);
            return false;
        }
    }
}
