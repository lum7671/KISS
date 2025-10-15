package fr.neamar.kiss.preference;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceDialogFragmentCompat;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * Dialog fragment for ResetPreferenceCompat.
 * Handles clearing the application history when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class ResetPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static ResetPreferenceDialogFragmentCompat newInstance(String key) {
        ResetPreferenceDialogFragmentCompat fragment = new ResetPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Clear the application history
            KissApplication.getApplication(requireContext()).getDataHandler().clearHistory();

            // Update preference summary
            if (getPreference() instanceof ResetPreferenceCompat) {
                getPreference().setSummary(requireContext().getString(R.string.history_erased));
            }

            // Show confirmation toast
            Toast.makeText(requireContext(), R.string.history_erased, Toast.LENGTH_LONG).show();
        }
    }
}
