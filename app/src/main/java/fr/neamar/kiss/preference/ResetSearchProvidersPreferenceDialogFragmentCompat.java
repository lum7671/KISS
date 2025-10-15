package fr.neamar.kiss.preference;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * Dialog fragment for ResetSearchProvidersPreferenceCompat.
 * Handles resetting search providers to defaults when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class ResetSearchProvidersPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static ResetSearchProvidersPreferenceDialogFragmentCompat newInstance(String key) {
        ResetSearchProvidersPreferenceDialogFragmentCompat fragment = new ResetSearchProvidersPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Remove the custom search providers preference
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .remove("available-search-providers").apply();

            // Show confirmation toast
            Toast.makeText(requireContext(), R.string.search_provider_reset_done_desc, Toast.LENGTH_LONG).show();

            // Reload search providers
            KissApplication.getApplication(requireContext()).getDataHandler().reloadSearchProvider();
        }
    }
}
