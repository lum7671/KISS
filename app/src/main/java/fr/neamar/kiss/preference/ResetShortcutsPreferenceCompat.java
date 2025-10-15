package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based ResetShortcutsPreference.
 * Shows a confirmation dialog before regenerating all app shortcuts.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetShortcutsPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetShortcutsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetShortcutsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetShortcutsPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetShortcutsPreferenceCompat(Context context) {
        super(context);
    }
}
