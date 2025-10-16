package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

/**
 * AndroidX-compatible version of ExportSettingsPreference.
 * Exports KISS settings to clipboard as JSON.
 */
public class ExportSettingsPreferenceCompat extends DialogPreference {

    public ExportSettingsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ExportSettingsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ExportSettingsPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle);
    }

    public ExportSettingsPreferenceCompat(Context context) {
        this(context, null);
    }
}
