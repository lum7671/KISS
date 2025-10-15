package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

import fr.neamar.kiss.R;

/**
 * AndroidX-based ResetPreference.
 * Shows a confirmation dialog before clearing application history.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetPreferenceCompat(Context context) {
        super(context);
    }
}
