package fr.neamar.kiss.preference;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * Dialog fragment for ResetFavoritesPreferenceCompat.
 * Handles clearing the favorite apps list when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class ResetFavoritesPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    private static final String TAG = ResetFavoritesPreferenceDialogFragmentCompat.class.getSimpleName();

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static ResetFavoritesPreferenceDialogFragmentCompat newInstance(String key) {
        ResetFavoritesPreferenceDialogFragmentCompat fragment = new ResetFavoritesPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Clear the favorite apps list
            PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .putString("favorite-apps-list", "").apply();

            // Reload apps to reflect the changes
            try {
                KissApplication.getApplication(requireContext()).getDataHandler().reloadApps();
            } catch (NullPointerException e) {
                Log.e(TAG, "Unable to reset favorites", e);
            }

            // Show confirmation toast
            Toast.makeText(requireContext(), R.string.favorites_erased, Toast.LENGTH_LONG).show();
        }
    }
}
