package fr.neamar.kiss;

import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
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
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.neamar.kiss.broadcast.IncomingCallHandler;
import fr.neamar.kiss.dataprovider.simpleprovider.SearchProvider;
import fr.neamar.kiss.dataprovider.simpleprovider.TagsProvider;
import fr.neamar.kiss.forwarder.ExperienceTweaks;
import fr.neamar.kiss.forwarder.TagsMenu;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.NameComparator;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.TagDummyPojo;
import fr.neamar.kiss.preference.ExcludePreferenceScreenCompat;
import fr.neamar.kiss.preference.SwitchPreferenceCompat;
import fr.neamar.kiss.utils.CoroutineUtils;
import fr.neamar.kiss.utils.DrawableUtils;
import fr.neamar.kiss.utils.MimeTypeUtils;
import fr.neamar.kiss.utils.Permission;
import fr.neamar.kiss.utils.ShortcutUtil;
import fr.neamar.kiss.utils.VersionInfo;

/**
 * AndroidX-based settings fragment.
 * Replaces PreferenceActivity with modern Fragment architecture.
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = SettingsFragment.class.getSimpleName();

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

    private static Pair<CharSequence[], CharSequence[]> ItemToRunListContent = null;

    private SharedPreferences prefs;
    private boolean requireFullRestart = false;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // Initialize preferences
        prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
        
        // Load preferences from XML
        setPreferencesFromResource(R.xml.preferences, rootKey);
        
        // Update ActionBar title based on root key
        if (rootKey != null && getActivity() != null) {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen != null && screen.getTitle() != null) {
                requireActivity().setTitle(screen.getTitle());
            }
        }

        // Remove API-level conditional preferences
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference("gestures-holder", "double-tap");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            removePreference("colors-section", "black-notification-icons");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            removePreference("icons-section", DrawableUtils.KEY_THEMED_ICONS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // Android 15+에서는 edge-to-edge 앱에서 상태바 색상 변경 불가
            removePreference("colors-section", "notification-bar-color");
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
                SettingsFragment.this.requireActivity().runOnUiThread(() -> iconsPack.setEnabled(true));
            }

            SettingsFragment.this.setAdditionalContactsData();
            SettingsFragment.this.addCustomSearchProvidersPreferences(prefs);

            SettingsFragment.this.addHiddenTagsTogglesInformation(prefs);
            SettingsFragment.this.addTagsFavInformation();
        };

        // This is reaaally slow, and always need to run asynchronously
        Runnable alwaysAsync = () -> {
            // TODO: Note that there is a bug here with all of these settings pages:
            //  These settings pages load the list of AppPojos from DataHandler only once.
            //  This means that the data shown in these settings pages will be stale if the AppPojo
            //  data stored in DataHandler is changed by elsewhere in the app.
            //  You can easily reproduce this bug by:
            //  1. Open the 'apps excluded from KISS' page
            //  2. Change some values from their defaults
            //  3. Go back and use the 'reset apps excluded from KISS' button
            //  4. Open the 'apps excluded from KISS' page again. The data shown will be incorrect,
            //   as it won't have refreshed for the user having reset the list.
            //   This list will refresh if the user closes and re-opens KISS settings.
            SettingsFragment.this.addExcludedAppSettings();
            SettingsFragment.this.addExcludedFromHistoryAppSettings();
            SettingsFragment.this.addExcludedShortcutAppSettings();
        };

        reorderPreferencesWithDisplayDependency();

        if (savedInstanceState == null) {
            // Run asynchronously to open settings fast
            CoroutineUtils.execute(runnable);
            asyncInitItemToRunList();
        } else {
            // Run synchronously to ensure preferences can be restored from state
            runnable.run();
            synchronized (SettingsFragment.class) {
                if (ItemToRunListContent == null)
                    ItemToRunListContent = generateItemToRunListContent();

                for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY) {
                    updateItemToRunList(gesturePref);
                }
            }
        }
        CoroutineUtils.execute(alwaysAsync);
        
        // 버전 정보 설정
        setupVersionInfo();
    }
    
    private void setupVersionInfo() {
        try {
            Preference versionPref = findPreference("version-info");
            if (versionPref != null) {
                String simpleVersion = VersionInfo.getSimpleVersionInfo();
                String fullVersion = VersionInfo.getFullVersionInfo();
                
                // 간단한 버전 정보를 title에, 상세 정보를 summary에 표시
                versionPref.setTitle("KISS " + simpleVersion);
                versionPref.setSummary(fullVersion);
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Version info set successfully");
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "version-info preference not found");
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Error setting up version info", e);
            }
            // 오류 발생 시 기본 정보만 표시
            Preference versionPref = findPreference("version-info");
            if (versionPref != null) {
                versionPref.setTitle("KISS Launcher");
                versionPref.setSummary("Version information not available");
            }
        }
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
        
        // Flag restart if needed
        if (requireFullRestart) {
            prefs.edit().putBoolean("require-layout-update", true).apply();
            requireFullRestart = false;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(@NonNull Preference preference) {
        String key = preference.getKey();
        
        // Log all preference clicks for debugging
        Log.d(TAG, "Preference clicked: " + key + ", type: " + preference.getClass().getSimpleName());
        
        // Handle PreferenceScreen navigation
        if (preference instanceof PreferenceScreen) {
            Log.d(TAG, "PreferenceScreen clicked: " + key);
            // Navigate to sub-screen by creating new fragment instance
            PreferenceScreen preferenceScreen = (PreferenceScreen) preference;
            
            // Create new fragment for sub-screen
            SettingsFragment subFragment = new SettingsFragment();
            Bundle args = new Bundle();
            args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
            subFragment.setArguments(args);
            
            // Replace current fragment with sub-screen in the proper container
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, subFragment)
                    .addToBackStack(null)
                    .commit();
            
            return true;
        }
        
        // Handle custom dialog preferences that don't have DialogFragment implementations
        if (preference instanceof fr.neamar.kiss.preference.ImportSettingsPreferenceCompat) {
            handleImportSettings();
            return true;
        } else if (preference instanceof fr.neamar.kiss.preference.ExportSettingsPreferenceCompat) {
            handleExportSettings();
            return true;
        } else if (preference instanceof fr.neamar.kiss.preference.RestartPreferenceCompat) {
            handleRestartApp();
            return true;
        } else if (preference instanceof fr.neamar.kiss.preference.ColorPreferenceCompat) {
            handleColorPicker((fr.neamar.kiss.preference.ColorPreferenceCompat) preference);
            return true;
        } else if (preference instanceof fr.neamar.kiss.preference.AddSearchProviderPreferenceCompat) {
            handleAddSearchProvider();
            return true;
        }
        
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null) {
            KissApplication.getApplication(requireContext()).getIconsHandler().onPrefChanged(sharedPreferences, key);

            if (PREF_LISTS_WITH_DEPENDENCY.contains(key)) {
                updateItemToRunList(key);
            }

            if (key.equalsIgnoreCase("available-search-providers")) {
                addCustomSearchProvidersPreferences(prefs);
                getDataHandler().reloadSearchProvider();
            } else if (key.equalsIgnoreCase("selected-search-provider-names")) {
                removeSearchProviderDefault(); // in order to refresh default search engine choices
                addDefaultSearchProvider(prefs);
                getDataHandler().reloadSearchProvider();
            } else if (key.equalsIgnoreCase("enable-phone-history")) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (enabled && !Permission.checkPermission(requireActivity(), Permission.PERMISSION_READ_PHONE_STATE)) {
                    Permission.askPermission(Permission.PERMISSION_READ_PHONE_STATE, new Permission.PermissionResultListener() {
                        @Override
                        public void onGranted() {
                            setPhoneHistoryEnabled(true);
                        }

                        @Override
                        public void onDenied() {
                            // You don't want to give us permission, that's fine. Revert the toggle.
                            SwitchPreferenceCompat p = (SwitchPreferenceCompat) findPreference(key);
                            if (p != null) {
                                p.setChecked(false);
                            }
                            Toast.makeText(requireContext(), R.string.permission_denied, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    setPhoneHistoryEnabled(enabled);
                }
            } else if (key.equalsIgnoreCase("primary-color")) {
                UIColors.clearPrimaryColorCache();
            } else if (key.equalsIgnoreCase("number-of-display-elements")) {
                // Phase 2 Step 4: No longer need to clear cache
                // Each searcher instance reads preference value directly
                // Cache is per-instance, not static
            } else if (key.equalsIgnoreCase("default-search-provider")) {
                getDataHandler().reloadSearchProvider();
            } else if ("pref-fav-tags-list".equals(key)) {
                // after we edit the fav tags list update DataHandler
                Set<String> favTags = sharedPreferences.getStringSet(key, Collections.<String>emptySet());
                DataHandler dh = getDataHandler();
                List<Pojo> favoritesPojo = dh.getFavorites();
                for (Pojo pojo : favoritesPojo)
                    if (pojo instanceof TagDummyPojo && !favTags.contains(pojo.getName()))
                        dh.removeFromFavorites(pojo.id);
                for (String tagName : favTags)
                    dh.addToFavorites(TagsProvider.generateUniqueId(tagName));
            } else if ("exclude-favorites-apps".equals(key)) {
                getDataHandler().reloadApps();
            } else if ("enable-notification-history".equals(key)) {
                boolean enabled = sharedPreferences.getBoolean(key, false);
                if (enabled) {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
            } else if ("selected-contact-mime-types".equals(key)) {
                getDataHandler().reloadContactsProvider();
            }
        }

        if (settingsRequiringRestart.contains(key) || settingsRequiringRestartForSettingsActivity.contains(key)) {
            requireFullRestart = true;

            if (settingsRequiringRestartForSettingsActivity.contains(key)) {
                // Kill this activity too, and restart
                requireActivity().recreate();
            }
        }
    }

    protected void setPhoneHistoryEnabled(boolean enabled) {
        IncomingCallHandler.setEnabled(requireContext(), enabled);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && enabled) {
            RoleManager roleManager = (RoleManager) requireActivity().getSystemService(android.content.Context.ROLE_SERVICE);
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, 1);
        }
    }

    private void fixSummaries() {
        int historyLength = getDataHandler().getHistoryLength();
        if (historyLength > 5) {
            Preference resetPreference = findPreference("reset");
            if (resetPreference != null) {
                resetPreference.setSummary(String.format(getString(R.string.items_title), historyLength));
            }
        }

        // Only display "rate the app" preference if the user has been using KISS long enough to enjoy it ;)
        Preference rateApp = findPreference("rate-app");
        if (rateApp != null) {
            if (historyLength < 300) {
                PreferenceScreen preferenceScreen = getPreferenceScreen();
                if (preferenceScreen != null) {
                    preferenceScreen.removePreference(rateApp);
                }
            } else {
                rateApp.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=" + requireContext().getPackageName()));
                    startActivity(intent);

                    return true;
                });
            }
        }
    }

    private void setListPreferenceIconsPacksData(ListPreference lp) {
        IconsHandler iph = KissApplication.getApplication(requireContext()).getIconsHandler();

        CharSequence[] entries;
        CharSequence[] entryValues;
        int i;

        {
            entries = new CharSequence[iph.getIconsPacks().size() + 1];
            entryValues = new CharSequence[iph.getIconsPacks().size() + 1];

            i = 0;
            entries[0] = this.getString(R.string.icons_pack_default_name);
            entryValues[0] = "default";
        }

        for (String packageIconsPack : iph.getIconsPacks().keySet()) {
            entries[++i] = iph.getIconsPacks().get(packageIconsPack);
            entryValues[i] = packageIconsPack;
        }

        lp.setEntries(entries);
        lp.setDefaultValue("default");
        lp.setEntryValues(entryValues);
    }

    private void setAdditionalContactsData() {
        // get all supported mime types
        Set<String> supportedMimeTypes = MimeTypeUtils.getSupportedMimeTypes(requireContext());

        // get all labels
        MimeTypeCache mimeTypeCache = KissApplication.getMimeTypeCache(requireContext());
        Map<String, String> uniqueLabels = mimeTypeCache.getUniqueLabels(requireContext(), supportedMimeTypes);

        // get entries and values for sorted mime types
        List<String> sortedMimeTypes = new ArrayList<>(supportedMimeTypes);
        Collections.sort(sortedMimeTypes);

        String[] mimeTypeEntries = new String[supportedMimeTypes.size()];
        String[] mimeTypeEntryValues = new String[supportedMimeTypes.size()];
        int pos = 0;
        for (String mimeType : sortedMimeTypes) {
            mimeTypeEntries[pos] = uniqueLabels.get(mimeType);
            mimeTypeEntryValues[pos] = mimeType;
            pos++;
        }

        MultiSelectListPreference multiPreference = (MultiSelectListPreference) findPreference("selected-contact-mime-types");
        if (multiPreference != null) {
            if (supportedMimeTypes.isEmpty()) {
                multiPreference.setEnabled(false);
            }
            multiPreference.setEntries(mimeTypeEntries);
            multiPreference.setEntryValues(mimeTypeEntryValues);
        }
    }

    /**
     * Because we use the order to insert preferences we need to have gaps in the original order
     */
    private void reorderPreferencesWithDisplayDependency() {
        // get groups that need gaps
        HashSet<PreferenceGroup> groups = new HashSet<>();
        for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY) {
            Preference pref = findPreference(gesturePref);
            if (pref != null) {
                PreferenceGroup parent = getParent(pref);
                if (parent != null) {
                    groups.add(parent);
                }
            }
        }
        // set new order numbers
        for (PreferenceGroup group : groups) {
            int count = group.getPreferenceCount();
            for (int idx = 0; idx < count; idx += 1) {
                Preference pref = group.getPreference(idx);
                pref.setOrder(idx * 10);
            }
        }
    }

    private Pair<CharSequence[], CharSequence[]> generateItemToRunListContent() {
        List<AppPojo> appPojoList = getDataHandler().getApplications();
        if (appPojoList == null)
            appPojoList = Collections.emptyList();

        // appPojoList is a copy of the original list; we can sort it in place
        Collections.sort(appPojoList, new NameComparator());

        // generate entry names and entry values
        final int appCount = appPojoList.size();
        CharSequence[] entries = new CharSequence[appCount];
        CharSequence[] entryValues = new CharSequence[appCount];
        for (int idx = 0; idx < appCount; idx++) {
            AppPojo appEntry = appPojoList.get(idx);
            entries[idx] = appEntry.getName();
            entryValues[idx] = appEntry.id;
        }
        return new Pair<>(entries, entryValues);
    }

    private void asyncInitItemToRunList() {
        final Runnable updateLists = () -> {
            for (String gesturePref : PREF_LISTS_WITH_DEPENDENCY)
                updateItemToRunList(gesturePref);
        };
        if (ItemToRunListContent == null) {
            CoroutineUtils.runAsync(
                () -> {
                    Pair<CharSequence[], CharSequence[]> content = generateItemToRunListContent();
                    synchronized (SettingsFragment.class) {
                        if (ItemToRunListContent == null)
                            ItemToRunListContent = content;
                    }
                },
                () -> updateLists.run()
            );
        } else {
            updateLists.run();
        }
    }

    private void updateItemToRunList(String key) {
        synchronized (SettingsFragment.class) {
            if (ItemToRunListContent != null)
                updateListPrefDependency(key, prefs.getString(key, null), "launch-pojo", key + "-launch-id", ItemToRunListContent);
        }
    }

    private void updateListPrefDependency(@NonNull String dependOnKey, @Nullable String dependOnValue, @NonNull String enableValue, @NonNull String listKey, @Nullable Pair<CharSequence[], CharSequence[]> listContent) {
        Preference prefEntryToRun = findPreference(listKey);

        if (prefEntryToRun == null && enableValue.equals(dependOnValue)) {
            prefEntryToRun = new ListPreference(requireContext());
            prefEntryToRun.setKey(listKey);
            prefEntryToRun.setTitle(R.string.gesture_launch_pojo);

            Preference pref = findPreference(dependOnKey);
            if (pref != null) {
                // set the list pref under the depended preference
                prefEntryToRun.setOrder(pref.getOrder() + 1);

                PreferenceGroup parent = getParent(pref);
                if (parent != null) {
                    parent.addPreference(prefEntryToRun);
                }
            }
        }

        if (prefEntryToRun instanceof ListPreference) {
            if (enableValue.equals(dependOnValue)) {
                if (listContent != null) {
                    CharSequence[] entries = listContent.first;
                    CharSequence[] entryValues = listContent.second;
                    ((ListPreference) prefEntryToRun).setEntries(entries);
                    ((ListPreference) prefEntryToRun).setEntryValues(entryValues);
                }
            } else {
                PreferenceGroup parent = getParent(prefEntryToRun);
                if (parent != null) {
                    parent.removePreference(prefEntryToRun);
                }
            }
        } else if (prefEntryToRun != null) {
            throw new IllegalStateException("Preference `" + listKey + "` is " + prefEntryToRun.getClass() + "; should be " + ListPreference.class);
        }
    }

    private void removePreference(String parentKey, String key) {
        PreferenceGroup p = (PreferenceGroup) findPreference(parentKey);
        if (p != null) {
            Preference c = p.findPreference(key);
            if (c != null) {
                p.removePreference(c);
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Preference to remove not found: " + parentKey + "/" + key);
                }
            }
        }
    }

    private PreferenceGroup getParent(Preference preference) {
        PreferenceScreen screen = getPreferenceScreen();
        return screen != null ? getParent(screen, preference) : null;
    }

    private static PreferenceGroup getParent(PreferenceGroup root, Preference preference) {
        for (int i = 0; i < root.getPreferenceCount(); i++) {
            Preference p = root.getPreference(i);
            if (p == preference)
                return root;
            if (p instanceof PreferenceGroup) {
                PreferenceGroup parent = getParent((PreferenceGroup) p, preference);
                if (parent != null)
                    return parent;
            }
        }
        return null;
    }

    private void addExcludedAppSettings() {
        final DataHandler dataHandler = getDataHandler();

        PreferenceScreen excludedAppsScreen = ExcludePreferenceScreenCompat.getInstance(
                requireContext(),
                getPreferenceManager(),
                R.string.ui_excluded_apps,
                R.string.ui_excluded_apps_dialog_title,
                new ExcludePreferenceScreenCompat.OnExcludedListener() {
                    @Override
                    public void onExcluded(final @NonNull AppPojo app) {
                        dataHandler.addToExcluded(app);
                    }

                    @Override
                    public void onIncluded(final @NonNull AppPojo app) {
                        dataHandler.removeFromExcluded(app);
                    }
                },
                AppPojo::isExcluded
        );

        PreferenceGroup category = (PreferenceGroup) findPreference("exclude_apps_category");
        if (category != null) {
            category.addPreference(excludedAppsScreen);
        }
    }

    private void addExcludedFromHistoryAppSettings() {
        final DataHandler dataHandler = getDataHandler();

        PreferenceScreen excludedAppsScreen = ExcludePreferenceScreenCompat.getInstance(
                requireContext(),
                getPreferenceManager(),
                R.string.ui_excluded_from_history_apps,
                R.string.ui_excluded_apps_dialog_title,
                new ExcludePreferenceScreenCompat.OnExcludedListener() {
                    @Override
                    public void onExcluded(final @NonNull AppPojo app) {
                        dataHandler.addToExcludedFromHistory(app);
                    }

                    @Override
                    public void onIncluded(final @NonNull AppPojo app) {
                        dataHandler.removeFromExcludedFromHistory(app);
                    }
                },
                AppPojo::isExcludedFromHistory
        );

        PreferenceGroup category = (PreferenceGroup) findPreference("exclude_apps_category");
        if (category != null) {
            category.addPreference(excludedAppsScreen);
        }
    }

    private void addExcludedShortcutAppSettings() {
        if (!ShortcutUtil.canDeviceShowShortcuts()) {
            return;
        }

        final DataHandler dataHandler = getDataHandler();

        PreferenceScreen excludedAppsScreen = ExcludePreferenceScreenCompat.getInstance(
                requireContext(),
                getPreferenceManager(),
                R.string.ui_excluded_from_shortcuts_apps,
                R.string.ui_excluded_apps_dialog_title,
                new ExcludePreferenceScreenCompat.OnExcludedListener() {
                    @Override
                    public void onExcluded(final @NonNull AppPojo app) {
                        dataHandler.addToExcludedShortcutApps(app);
                    }

                    @Override
                    public void onIncluded(final @NonNull AppPojo app) {
                        dataHandler.removeFromExcludedShortcutApps(app);
                    }
                },
                AppPojo::isExcludedShortcuts
        );

        PreferenceGroup category = (PreferenceGroup) findPreference("exclude_apps_category");
        if (category != null) {
            category.addPreference(excludedAppsScreen);
        }
    }

    private void addCustomSearchProvidersPreferences(SharedPreferences prefs) {
        if (prefs.getStringSet("selected-search-provider-names", null) == null) {
            // If null, it means this setting has never been accessed before
            // In this case, null != [] ([] happens when the user manually unselected every single option)
            // So, when null, we know it's the first time opening this setting and we can write the default value.
            // note: other preferences are initialized automatically in MainActivity.onCreate() from the preferences XML,
            // but this preference isn't defined in the XML so can't be initialized that easily.
            prefs.edit().putStringSet("selected-search-provider-names", SearchProvider.getSelectedSearchProviders(prefs)).apply();
        }

        removeSearchProviderSelect();
        removeSearchProviderDelete();
        removeSearchProviderDefault();
        addCustomSearchProvidersSelect(prefs);
        addCustomSearchProvidersDelete(prefs);
        addDefaultSearchProvider(prefs);
    }

    private void removeSearchProviderSelect() {
        removePreference("web-providers", "selected-search-provider-names");
    }

    private void removeSearchProviderDelete() {
        removePreference("web-providers", "deleting-search-providers-names");
    }

    private void removeSearchProviderDefault() {
        removePreference("web-providers", "default-search-provider");
    }

    private void addCustomSearchProvidersSelect(SharedPreferences prefs) {
        MultiSelectListPreference multiPreference = createCustomSearchProvidersPreference("selected-search-provider-names", R.string.search_providers_title, 10);
        PreferenceGroup category = (PreferenceGroup) findPreference("web-providers");
        if (category != null) {
            category.addPreference(multiPreference);
        }
    }

    private void addCustomSearchProvidersDelete(final SharedPreferences prefs) {
        MultiSelectListPreference multiPreference = createCustomSearchProvidersPreference("deleting-search-providers-names", R.string.search_providers_delete, 20);
        multiPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> searchProvidersToDelete = (Set<String>) newValue;

                Set<String> availableSearchProviders = SearchProvider.getAvailableSearchProviders(requireContext(), prefs);
                Set<String> updatedProviders = SearchProvider.getAvailableSearchProviders(requireContext(), prefs);

                for (String searchProvider : availableSearchProviders) {
                    for (String providerToDelete : searchProvidersToDelete) {
                        if (searchProvider.startsWith(providerToDelete + "|")) {
                            updatedProviders.remove(searchProvider);
                        }
                    }
                }
                SharedPreferences.Editor editor = prefs.edit();
                editor.putStringSet("available-search-providers", updatedProviders);
                editor.putStringSet("deleting-search-providers-names", updatedProviders);
                editor.apply();

                if (!searchProvidersToDelete.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.search_provider_deleted, Toast.LENGTH_LONG).show();
                }
            }

            return true;
        });

        PreferenceGroup category = (PreferenceGroup) findPreference("web-providers");
        if (category != null) {
            category.addPreference(multiPreference);
        }
    }

    private MultiSelectListPreference createCustomSearchProvidersPreference(@NonNull String key, @StringRes int title, int order) {
        MultiSelectListPreference multiPreference = new MultiSelectListPreference(requireContext());
        //get stored search providers or default hard-coded values
        Set<String> availableSearchProviders = SearchProvider.getAvailableSearchProviders(requireContext(), prefs);
        String[] searchProvidersArray = new String[availableSearchProviders.size()];
        int pos = 0;
        //get names of search providers
        for (String searchProvider : availableSearchProviders) {
            searchProvidersArray[pos++] = searchProvider.split("\\|")[0];
        }
        multiPreference.setEnabled(!availableSearchProviders.isEmpty());
        String search_providers_title = this.getString(title);
        multiPreference.setTitle(search_providers_title);
        multiPreference.setDialogTitle(search_providers_title);
        multiPreference.setKey(key);
        multiPreference.setEntries(searchProvidersArray);
        multiPreference.setEntryValues(searchProvidersArray);
        multiPreference.setOrder(order);
        return multiPreference;
    }

    private void addDefaultSearchProvider(final SharedPreferences prefs) {
        ListPreference standardPref = new ListPreference(requireContext());

        // Get selected providers to choose from
        Set<String> selectedProviders = SearchProvider.getSelectedSearchProviders(prefs);
        String[] selectedProviderArray = new String[selectedProviders.size()];
        int pos = 0;
        //get names of search providers
        for (String searchProvider : selectedProviders) {
            selectedProviderArray[pos++] = searchProvider.split("\\|")[0];
        }

        String searchProvidersTitle = this.getString(R.string.search_provider_default);
        standardPref.setTitle(searchProvidersTitle);
        standardPref.setDialogTitle(searchProvidersTitle);
        standardPref.setKey("default-search-provider");
        standardPref.setEntries(selectedProviderArray);
        standardPref.setEntryValues(selectedProviderArray);
        standardPref.setOrder(0);
        standardPref.setDefaultValue("Google"); // Google is standard on install

        PreferenceGroup category = (PreferenceGroup) findPreference("web-providers");
        if (category != null) {
            category.addPreference(standardPref);
        }
    }

    private void addHiddenTagsTogglesInformation(SharedPreferences prefs) {
        Set<String> menuTags = TagsMenu.getPrefTags(prefs, requireContext());
        MultiSelectListPreference selectListPreference = (MultiSelectListPreference) findPreference("pref-toggle-tags-list");
        if (selectListPreference == null) {
            return;
        }
        
        Set<String> tagsSet = getDataHandler()
                .getTagsHandler()
                .getAllTagsAsSet();

        // append tags that are available to toggle now
        tagsSet.addAll(menuTags);

        String[] tagArray = tagsSet.toArray(new String[0]);
        Arrays.sort(tagArray);
        selectListPreference.setEntries(tagArray);
        selectListPreference.setEntryValues(tagArray);
        selectListPreference.setValues(menuTags);

        // Enable the preference
        requireActivity().runOnUiThread(() -> {
            selectListPreference.setEnabled(true);
            selectListPreference.setTitle(R.string.pref_toggle_tags_select);
        });
    }

    private void addTagsFavInformation() {
        Set<String> favTags = getFavTags();
        final MultiSelectListPreference selectListPreference = (MultiSelectListPreference) findPreference("pref-fav-tags-list");
        if (selectListPreference == null) {
            return;
        }

        Set<String> tagsSet = getDataHandler()
                .getTagsHandler()
                .getAllTagsAsSet();

        // make sure we can toggle off the tags that are in the favs now
        tagsSet.addAll(favTags);

        String[] tagArray = tagsSet.toArray(new String[0]);
        Arrays.sort(tagArray);
        selectListPreference.setEntries(tagArray);
        selectListPreference.setEntryValues(tagArray);
        selectListPreference.setValues(favTags);

        // Enable the preference
        requireActivity().runOnUiThread(() -> {
            selectListPreference.setEnabled(true);
            selectListPreference.setTitle(R.string.pref_fav_tags_select);
        });
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

    // ========== Custom Dialog Preference Handlers ==========
    
    /**
     * Handle Import Settings preference click
     */
    private void handleImportSettings() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_settings)
                .setMessage(R.string.import_settings_dialog)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    importSettingsFromClipboard();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    /**
     * Import settings from clipboard JSON
     */
    private void importSettingsFromClipboard() {
        try {
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            String clipboardText = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext()).toString();

            // Validate JSON
            org.json.JSONObject jsonObject = new org.json.JSONObject(clipboardText);
            int minVersion = jsonObject.optInt("__v", -1);
            if (minVersion < 0) {
                Toast.makeText(requireContext(), R.string.import_settings_version_missing, Toast.LENGTH_LONG).show();
                return;
            } else if (minVersion > fr.neamar.kiss.BuildConfig.VERSION_CODE) {
                Toast.makeText(requireContext(), R.string.import_settings_upgrade_kiss, Toast.LENGTH_LONG).show();
                return;
            }

            // Reset everything to default
            SharedPreferences oldPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
            if (oldPrefs.edit().clear().commit()) {
                androidx.preference.PreferenceManager.setDefaultValues(requireContext(), R.xml.preferences, true);
            }

            // Set imported values
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
            SharedPreferences.Editor editor = prefs.edit();

            java.util.Iterator<?> keys = jsonObject.keys();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                if (key.startsWith("__")) {
                    continue;
                }

                Object newValue = jsonObject.get(key);
                Object currentValue = prefs.getAll().get(key);
                if (newValue instanceof Boolean) {
                    if (hasMatchingType(key, currentValue, Boolean.class)) {
                        editor.putBoolean(key, (Boolean) newValue);
                    }
                } else if (newValue instanceof String) {
                    if (hasMatchingType(key, currentValue, String.class)) {
                        editor.putString(key, (String) newValue);
                    }
                } else if (newValue instanceof org.json.JSONArray) {
                    if (hasMatchingType(key, currentValue, Set.class)) {
                        org.json.JSONArray newValues = (org.json.JSONArray) newValue;
                        Set<String> unwrappedValues = new java.util.HashSet<>(newValues.length());
                        for (int i = 0; i < newValues.length(); i++) {
                            unwrappedValues.add(newValues.getString(i));
                        }
                        editor.putStringSet(key, unwrappedValues);
                    }
                } else {
                    Log.w(TAG, "Unknown type: " + key + ":" + newValue);
                }
            }
            
            if (!editor.commit()) {
                Toast.makeText(requireContext(), R.string.import_settings_save_not_possible, Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(), R.string.import_settings_done, Toast.LENGTH_SHORT).show();
            
            // Recreate activity to apply imported settings
            requireActivity().recreate();
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.import_settings_error, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Import settings failed", e);
        }
    }
    
    private boolean hasMatchingType(String key, Object currentValue, Class<?> expectedType) {
        if (currentValue == null) {
            Log.w(TAG, "Unknown preference: " + key);
            return false;
        }
        return expectedType.isInstance(currentValue);
    }
    
    /**
     * Handle Export Settings preference click
     */
    private void handleExportSettings() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.export_settings)
                .setMessage("Export current settings and tags to clipboard")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    exportSettingsToClipboard();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    /**
     * Export settings to clipboard as JSON
     */
    private void exportSettingsToClipboard() {
        try {
            SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());
            Map<String, ?> allPrefs = prefs.getAll();
            
            org.json.JSONObject jsonObject = new org.json.JSONObject();
            jsonObject.put("__v", fr.neamar.kiss.BuildConfig.VERSION_CODE);
            
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                if (entry.getValue() instanceof Set) {
                    jsonObject.put(entry.getKey(), new org.json.JSONArray((Set<?>) entry.getValue()));
                } else {
                    jsonObject.put(entry.getKey(), entry.getValue());
                }
            }
            
            android.content.ClipboardManager clipboard = 
                (android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("KISS Settings", jsonObject.toString());
            clipboard.setPrimaryClip(clip);
            
            Toast.makeText(requireContext(), R.string.export_settings_done, Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Export settings failed", e);
        }
    }
    
    /**
     * Handle Restart App preference click
     */
    private void handleRestartApp() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.restart_name))
                .setMessage(getString(R.string.restart_warn))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    System.exit(0);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    /**
     * Handle Color Picker preference click
     */
    private void handleColorPicker(fr.neamar.kiss.preference.ColorPreferenceCompat preference) {
        // For now, just show a toast - full color picker implementation would require more work
        Toast.makeText(requireContext(), "Color picker coming soon", Toast.LENGTH_SHORT).show();
        // TODO: Implement color picker dialog
    }
    
    /**
     * Handle Add Search Provider preference click
     */
    private void handleAddSearchProvider() {
        // For now, just show a toast - full implementation would require more work
        Toast.makeText(requireContext(), "Add search provider coming soon", Toast.LENGTH_SHORT).show();
        // TODO: Implement search provider dialog
    }
}
