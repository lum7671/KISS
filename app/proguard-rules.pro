# ======================================================================
# Phase 6 Step 8: APK Size Optimization
# ======================================================================

# javax.annotation 패키지 전체 유지
-keep class javax.annotation.** { *; }

# R8 missing_rules.txt 제안 적용
-dontwarn javax.annotation.Nullable

# 데이터 관련 Provider 클래스 난독화/제거 방지
-keep class fr.neamar.kiss.dataprovider.AppProvider { *; }
-keep class fr.neamar.kiss.dataprovider.ContactsProvider { *; }
-keep class fr.neamar.kiss.dataprovider.ShortcutsProvider { *; }

# ======================================================================
# Preference Optimization (Phase 6)
# ======================================================================

# AndroidX Preference 최적화 - XML에서 리플렉션으로 생성되는 클래스 유지
-keepclassmembers class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# DialogFragment 최적화 - 필수 생성자 유지
-keep class * extends androidx.preference.PreferenceDialogFragmentCompat {
    public <init>();
}

# Legacy Preference 클래스 경고 억제 (이미 제거됨)
-dontwarn fr.neamar.kiss.preference.AddSearchProviderPreference
-dontwarn fr.neamar.kiss.preference.ColorPreference
-dontwarn fr.neamar.kiss.preference.DefaultLauncherPreference
-dontwarn fr.neamar.kiss.preference.ExportSettingsPreference
-dontwarn fr.neamar.kiss.preference.FreezeHistorySwitch
-dontwarn fr.neamar.kiss.preference.ImportSettingsPreference
-dontwarn fr.neamar.kiss.preference.NotificationPreference
-dontwarn fr.neamar.kiss.preference.ResetPreference
-dontwarn fr.neamar.kiss.preference.ResetExcludedAppShortcutsPreference
-dontwarn fr.neamar.kiss.preference.ResetExcludedAppsPreference
-dontwarn fr.neamar.kiss.preference.ResetExcludedFromHistoryAppsPreference
-dontwarn fr.neamar.kiss.preference.ResetFavoritesPreference
-dontwarn fr.neamar.kiss.preference.ResetSearchProvidersPreference
-dontwarn fr.neamar.kiss.preference.ResetShortcutsPreference
-dontwarn fr.neamar.kiss.preference.RestartPreference
-dontwarn fr.neamar.kiss.preference.RootModeSwitch
-dontwarn fr.neamar.kiss.preference.ShizukuModeSwitch
-dontwarn fr.neamar.kiss.preference.SwitchPreference

# Legacy SettingsActivity 경고 억제 (이미 제거됨)
-dontwarn fr.neamar.kiss.SettingsActivity

# ======================================================================
# Aggressive Optimization for Release Builds
# ======================================================================

# 미사용 코드 적극 제거
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Preference setter 최적화 (런타임에 사용되지 않는 경우)
-assumenosideeffects class * extends androidx.preference.Preference {
    public void setEnabled(boolean);
    public void setSelectable(boolean);
}