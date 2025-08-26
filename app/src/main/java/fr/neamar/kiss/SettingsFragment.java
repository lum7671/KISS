package fr.neamar.kiss;

import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fr.neamar.kiss.dataprovider.AppProvider;
import fr.neamar.kiss.dataprovider.ContactsProvider;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.TagDummyPojo;
import fr.neamar.kiss.preference.AddSearchProviderPreference;
import fr.neamar.kiss.preference.SwitchPreference;
import fr.neamar.kiss.searcher.QuerySearcher;
import fr.neamar.kiss.utils.DrawableUtils;
import fr.neamar.kiss.utils.MimeTypeUtils;
import fr.neamar.kiss.utils.Permission;
import fr.neamar.kiss.utils.ShortcutUtil;
import fr.neamar.kiss.utils.SystemUiVisibilityHelper;
import fr.neamar.kiss.utils.VersionInfo;

/**
 * Phase 2: AndroidX 마이그레이션
 * PreferenceActivity를 대체하는 PreferenceFragmentCompat 구현
 */
public class SettingsFragment extends PreferenceFragmentCompat 
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    // Those settings require the app to restart
    final static private List<String> settingsRequiringRestart = Arrays.asList("primary-color", "transparent-search", "transparent-favorites",
            "pref-rounded-list", "pref-rounded-bars", "pref-swap-kiss-button-with-menu", "pref-hide-circle", "history-hide",
            "enable-favorites-bar", "notification-bar-color", "black-notification-icons", "icons-pack", "theme-shadow",
            "theme-separator", "theme-result-color", "large-favorites-bar", "pref-hide-search-bar-hint", "theme-wallpaper",
            "theme-bar-color", "results-size", "large-result-list-margins", "themed-icons", "icons-hide", null);
    // Those settings require a restart of the settings
    final static private List<String> settingsRequiringRestartForSettingsActivity = Arrays.asList("theme", "force-portrait", null);

    private final static List<String> PREF_LISTS_WITH_DEPENDENCY = Arrays.asList(
            "gesture-up", "gesture-down",
            "gesture-left", "gesture-right",
            "gesture-long-press"
    );
    private static final String TAG = SettingsFragment.class.getSimpleName();
    private static Pair<CharSequence[], CharSequence[]> ItemToRunListContent = null;

    private boolean requireFullRestart = false;
    private SharedPreferences prefs;
    private Permission permissionManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        permissionManager = new Permission(requireActivity());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference("gestures-holder", "double-tap");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            removePreference("colors-section", "black-notification-icons");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            removePreference("icons-section", DrawableUtils.KEY_THEMED_ICONS);
        }
        if (!ShortcutUtil.canDeviceShowShortcuts()) {
            removePreference("exclude_apps_category", "reset-excluded-app-shortcuts");
            removePreference("search-providers", "enable-shortcuts");
            removePreference("search-providers", "reset");
        }

        final ListPreference iconsPack = (ListPreference) findPreference("icons-pack");
        if (iconsPack != null) {
            iconsPack.setEnabled(false);
        }

        Runnable runnable = () -> {
            SettingsFragment.this.fixSummaries();

            if (iconsPack != null) {
                SettingsFragment.this.setListPreferenceIconsPacksData(iconsPack);
                requireActivity().runOnUiThread(() -> iconsPack.setEnabled(true));
            }

            SettingsFragment.this.setAdditionalContactsData();
            SettingsFragment.this.addCustomSearchProvidersPreferences(prefs);

            SettingsFragment.this.addHiddenTagsTogglesInformation(prefs);
            SettingsFragment.this.addTagsFavInformation();
        };

        // This is reaaally slow, and always need to run asynchronously
        Runnable alwaysAsync = () -> {
            SettingsFragment.this.addExcludedAppSettings();
            SettingsFragment.this.addExcludedFromHistoryAppSettings();
            SettingsFragment.this.addExcludedShortcutAppSettings();
        };

        // Run async tasks
        new Thread(runnable).start();
        new Thread(alwaysAsync).start();
    }

    /**
     * Get tags that should be in the favorites bar
     *
     * @return what we find in DataHandler
     */
    @NonNull
    private Set<String> getFavTags() {
        List<Pojo> favoritesPojo = getDataHandler().getFavorites();
        Set<String> set = new HashSet<>();
        for (Pojo pojo : favoritesPojo) {
            if (pojo instanceof TagDummyPojo)
                set.add(pojo.getName());
        }
        return set;
    }

    private DataHandler getDataHandler() {
        return KissApplication.getApplication(requireContext()).getDataHandler();
    }

    // TODO: Phase 2 - 기존 SettingsActivity의 모든 메소드들을 여기로 이동해야 함
    // 임시로 스텁 메소드들 추가
    private void removePreference(String categoryKey, String preferenceKey) {
        // TODO: implement preference removal logic
        PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
            Preference category = screen.findPreference(categoryKey);
            if (category instanceof androidx.preference.PreferenceCategory) {
                androidx.preference.PreferenceCategory prefCategory = (androidx.preference.PreferenceCategory) category;
                Preference preference = prefCategory.findPreference(preferenceKey);
                if (preference != null) {
                    prefCategory.removePreference(preference);
                }
            }
        }
    }

    private void fixSummaries() {
        // TODO: implement summary fixing logic  
    }

    private void setListPreferenceIconsPacksData(ListPreference iconsPack) {
        // TODO: implement icons pack data setting
    }

    private void setAdditionalContactsData() {
        // TODO: implement additional contacts data
    }

    private void addCustomSearchProvidersPreferences(SharedPreferences prefs) {
        // TODO: implement custom search providers
    }

    private void addHiddenTagsTogglesInformation(SharedPreferences prefs) {
        // TODO: implement hidden tags toggles
    }

    private void addTagsFavInformation() {
        // TODO: implement tags fav information
    }

    private void addExcludedAppSettings() {
        // TODO: implement excluded app settings
    }

    private void addExcludedFromHistoryAppSettings() {
        // TODO: implement excluded from history app settings
    }

    private void addExcludedShortcutAppSettings() {
        // TODO: implement excluded shortcut app settings
    }

    @Override
    public void onResume() {
        super.onResume();
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // TODO: Phase 2 - implement preference change handling from SettingsActivity
    }
}
