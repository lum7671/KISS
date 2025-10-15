package fr.neamar.kiss.preference;

import android.os.Bundle;

import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * Dialog fragment for RestartPreferenceCompat.
 * Handles the application restart when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class RestartPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static RestartPreferenceDialogFragmentCompat newInstance(String key) {
        RestartPreferenceDialogFragmentCompat fragment = new RestartPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Restart the application by terminating the process
            System.exit(0);
        }
    }
}
