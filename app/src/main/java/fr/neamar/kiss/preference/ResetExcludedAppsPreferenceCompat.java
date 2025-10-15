package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based ResetExcludedAppsPreference.
 * Shows a confirmation dialog before clearing the excluded apps list.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetExcludedAppsPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetExcludedAppsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetExcludedAppsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetExcludedAppsPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetExcludedAppsPreferenceCompat(Context context) {
        super(context);
    }
}
