package fr.neamar.kiss.preference;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;
import androidx.preference.DialogPreference;

import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;

/**
 * AndroidX-compatible version of ColorPreference.
 * Allows user to select a color from a palette or predefined values.
 */
public class ColorPreferenceCompat extends DialogPreference {

    @ColorInt
    private int selectedColor = UIColors.COLOR_DEFAULT;

    public ColorPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setDialogLayoutResource(R.layout.pref_color);
    }

    public ColorPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ColorPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.dialogPreferenceStyle);
    }

    public ColorPreferenceCompat(Context context) {
        this(context, null);
    }

    @ColorInt
    public int getSelectedColor() {
        return selectedColor;
    }

    public void setSelectedColor(@ColorInt int color) {
        this.selectedColor = color;
        persistString(UIColors.colorToString(this.selectedColor));
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String colorString;
        if (defaultValue instanceof String) {
            colorString = (String) defaultValue;
        } else {
            colorString = getPersistedString(UIColors.colorToString(UIColors.COLOR_DEFAULT));
        }
        setSelectedColor(Color.parseColor(colorString));
    }
}
