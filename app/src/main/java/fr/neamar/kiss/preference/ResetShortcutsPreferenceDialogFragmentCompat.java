package fr.neamar.kiss.preference;

import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.PreferenceDialogFragmentCompat;

import fr.neamar.kiss.R;
import fr.neamar.kiss.utils.ShortcutUtil;

/**
 * Dialog fragment for ResetShortcutsPreferenceCompat.
 * Handles regenerating all app shortcuts when user confirms.
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 3.
 */
public class ResetShortcutsPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static ResetShortcutsPreferenceDialogFragmentCompat newInstance(String key) {
        ResetShortcutsPreferenceDialogFragmentCompat fragment = new ResetShortcutsPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Remove all existing shortcuts
            ShortcutUtil.removeAllShortcuts(requireContext());

            // Build all shortcuts again
            ShortcutUtil.addAllShortcuts(requireContext());

            // Show confirmation toast
            Toast.makeText(requireContext(), R.string.regenerate_shortcuts_done, Toast.LENGTH_LONG).show();
        }
    }
}
