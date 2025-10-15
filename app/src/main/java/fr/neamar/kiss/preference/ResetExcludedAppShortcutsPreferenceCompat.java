package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based ResetExcludedAppShortcutsPreference.
 * Shows a confirmation dialog before clearing the excluded app shortcuts list.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetExcludedAppShortcutsPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetExcludedAppShortcutsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetExcludedAppShortcutsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetExcludedAppShortcutsPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetExcludedAppShortcutsPreferenceCompat(Context context) {
        super(context);
    }
}
