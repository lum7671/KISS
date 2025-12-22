package fr.neamar.kiss.preference;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.AttributeSet;

import androidx.preference.Preference;

/**
 * AndroidX-based NotificationPreference.
 * Opens Android's notification listener settings when clicked.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.Preference
 * as part of Phase 6 Step 4 fix.
 * 
 * Note: Changed from DialogPreference to Preference because this preference
 * should directly open system settings, not show a custom dialog.
 */
public class NotificationPreferenceCompat extends Preference {

    public NotificationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public NotificationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public NotificationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NotificationPreferenceCompat(Context context) {
        super(context);
        init();
    }
    
    private void init() {
        // Set click listener to open notification listener settings
        setOnPreferenceClickListener(preference -> {
            Context context = getContext();
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            context.startActivity(intent);
            return true;
        });
    }
}
