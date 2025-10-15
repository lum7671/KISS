package fr.neamar.kiss.preference;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * Dialog fragment for ResetExcludedFromHistoryAppsPreferenceCompat.
 * Handles clearing the apps excluded from history list when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class ResetExcludedFromHistoryAppsPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static ResetExcludedFromHistoryAppsPreferenceDialogFragmentCompat newInstance(String key) {
        ResetExcludedFromHistoryAppsPreferenceDialogFragmentCompat fragment = new ResetExcludedFromHistoryAppsPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Clear the apps excluded from history list
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putStringSet("excluded-apps-from-history", null).apply();

            // Reload apps because the value is cached in AppPojo#excludedFromHistory
            KissApplication.getApplication(requireContext()).getDataHandler().reloadApps();

            // Show confirmation toast
            Toast.makeText(requireContext(), R.string.excluded_app_list_erased, Toast.LENGTH_LONG).show();
        }
    }
}
