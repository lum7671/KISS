package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based RestartPreference.
 * Shows a confirmation dialog before restarting the application.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class RestartPreferenceCompat extends androidx.preference.DialogPreference {

    public RestartPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public RestartPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RestartPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RestartPreferenceCompat(Context context) {
        super(context);
    }
}
