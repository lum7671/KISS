package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based NotificationPreference.
 * Shows a confirmation dialog before opening notification listener settings.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 4.
 */
public class NotificationPreferenceCompat extends androidx.preference.DialogPreference {

    public NotificationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NotificationPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NotificationPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NotificationPreferenceCompat(Context context) {
        super(context);
    }
}
