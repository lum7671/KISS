package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based DefaultLauncherPreference.
 * Shows a confirmation dialog before triggering the default launcher selection.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 4.
 */
public class DefaultLauncherPreferenceCompat extends androidx.preference.DialogPreference {

    public DefaultLauncherPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public DefaultLauncherPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DefaultLauncherPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DefaultLauncherPreferenceCompat(Context context) {
        super(context);
    }
}
