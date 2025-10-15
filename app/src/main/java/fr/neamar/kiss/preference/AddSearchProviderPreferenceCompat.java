package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import fr.neamar.kiss.R;

/**
 * AndroidX-compatible version of AddSearchProviderPreference.
 * Allows user to add custom search providers with validation.
 */
public class AddSearchProviderPreferenceCompat extends DialogPreference {

    public AddSearchProviderPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setPersistent(false);
    }

    public AddSearchProviderPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public AddSearchProviderPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle);
    }

    public AddSearchProviderPreferenceCompat(Context context) {
        this(context, null);
    }
}
