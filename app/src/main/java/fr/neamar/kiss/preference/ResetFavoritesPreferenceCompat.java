package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

/**
 * AndroidX-based ResetFavoritesPreference.
 * Shows a confirmation dialog before clearing favorite apps list.
 * 
 * Migration from android.preference.DialogPreference to androidx.preference.DialogPreference
 * as part of Phase 6 Step 3.
 */
public class ResetFavoritesPreferenceCompat extends androidx.preference.DialogPreference {

    public ResetFavoritesPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ResetFavoritesPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ResetFavoritesPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ResetFavoritesPreferenceCompat(Context context) {
        super(context);
    }
}
