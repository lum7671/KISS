package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based ResetSearchProvidersPreference.
 * Shows a confirmation dialog before resetting search providers to defaults.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetSearchProvidersPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetSearchProvidersPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetSearchProvidersPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetSearchProvidersPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetSearchProvidersPreferenceCompat(Context context) {
        super(context);
    }
}
