package fr.neamar.kiss.preference;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * Dialog fragment for ResetExcludedAppShortcutsPreferenceCompat.
 * Handles clearing the excluded app shortcuts list when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class ResetExcludedAppShortcutsPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static ResetExcludedAppShortcutsPreferenceDialogFragmentCompat newInstance(String key) {
        ResetExcludedAppShortcutsPreferenceDialogFragmentCompat fragment = new ResetExcludedAppShortcutsPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Clear the excluded app shortcuts list
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putStringSet(DataHandler.PREF_KEY_EXCLUDED_SHORTCUT_APPS, null).apply();

            DataHandler dataHandler = KissApplication.getApplication(requireContext()).getDataHandler();
            
            // Reload shortcuts to refresh the shortcuts shown in KISS
            dataHandler.reloadShortcuts();
            
            // Reload apps since the AppPojo.isExcludedShortcuts value also needs to be refreshed
            dataHandler.reloadApps();

            // Show confirmation toast
            Toast.makeText(requireContext(), R.string.excluded_app_list_erased, Toast.LENGTH_LONG).show();
        }
    }
}
