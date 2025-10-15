package fr.neamar.kiss.preference;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.preference.PreferenceDialogFragmentCompat;

/**
 * Dialog fragment for NotificationPreferenceCompat.
 * Opens the system's notification listener settings when user confirms.
 * 
 * This allows users to grant KISS Launcher permission to read notifications,
 * which is used for displaying notification badges and accessing notification content.
 * 
 * Migration from legacy onDialogClosed() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 4.
 */
public class NotificationPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static NotificationPreferenceDialogFragmentCompat newInstance(String key) {
        NotificationPreferenceDialogFragmentCompat fragment = new NotificationPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Open the notification listener settings page
            requireContext().startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }
}
