package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

/**
 * AndroidX-compatible version of ImportSettingsPreference.
 * Imports KISS settings from clipboard JSON.
 */
public class ImportSettingsPreferenceCompat extends DialogPreference {

    public ImportSettingsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ImportSettingsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ImportSettingsPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle);
    }

    public ImportSettingsPreferenceCompat(Context context) {
        this(context, null);
    }
}
