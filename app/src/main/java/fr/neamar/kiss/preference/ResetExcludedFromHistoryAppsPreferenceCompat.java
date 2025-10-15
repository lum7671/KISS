package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based ResetExcludedFromHistoryAppsPreference.
 * Shows a confirmation dialog before clearing the apps excluded from history list.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetExcludedFromHistoryAppsPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetExcludedFromHistoryAppsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetExcludedFromHistoryAppsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetExcludedFromHistoryAppsPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetExcludedFromHistoryAppsPreferenceCompat(Context context) {
        super(context);
    }
}
