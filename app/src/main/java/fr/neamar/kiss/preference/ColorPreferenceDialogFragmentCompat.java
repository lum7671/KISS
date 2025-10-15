package fr.neamar.kiss.preference;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.preference.PreferenceDialogFragmentCompat;

import com.android.colorpicker.ColorPickerDialog;
import com.android.colorpicker.ColorPickerPalette;
import com.android.colorpicker.ColorPickerSwatch.OnColorSelectedListener;

import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;

/**
 * Dialog fragment for ColorPreferenceCompat.
 * Displays a color picker with palette and predefined color buttons.
 */
public class ColorPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat implements OnColorSelectedListener {

    private ColorPickerPalette palette;

    @ColorInt
    private int selectedColor = UIColors.COLOR_DEFAULT;

    public static ColorPreferenceDialogFragmentCompat newInstance(String key) {
        ColorPreferenceDialogFragmentCompat fragment = new ColorPreferenceDialogFragmentCompat();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        // Get current color from preference
        ColorPreferenceCompat preference = (ColorPreferenceCompat) getPreference();
        this.selectedColor = preference.getSelectedColor();

        // Configure the color picker
        this.palette = view.findViewById(R.id.colorPicker);
        this.palette.init(ColorPickerDialog.SIZE_SMALL, 4, this);

        // Reconfigure color picker based on the available space
        view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            private boolean ignoreNextUpdate = false;

            public void onGlobalLayout() {
                if (this.ignoreNextUpdate) {
                    this.ignoreNextUpdate = false;
                    return;
                }

                // Calculate number of swatches to display
                int swatchSize = requireContext().getResources().getDimensionPixelSize(R.dimen.color_swatch_small);
                int swatchMargin = requireContext().getResources().getDimensionPixelSize(R.dimen.color_swatch_margins_small);
                ColorPreferenceDialogFragmentCompat.this.palette.init(ColorPickerDialog.SIZE_SMALL, view.getWidth() / (swatchSize + swatchMargin), ColorPreferenceDialogFragmentCompat.this);

                // Cause redraw and (by extension) also a layout recalculation
                this.ignoreNextUpdate = true;
                ColorPreferenceDialogFragmentCompat.this.drawPalette();
            }
        });

        // Bind click events from the custom color values
        Button buttonColorTransparentDark = view.findViewById(R.id.colorTransparentDark);
        buttonColorTransparentDark.setOnClickListener(v -> ColorPreferenceDialogFragmentCompat.this.onColorSelected(UIColors.COLOR_DARK_TRANSPARENT));

        Button buttonColorTransparentWhite = view.findViewById(R.id.colorTransparentWhite);
        buttonColorTransparentWhite.setOnClickListener(v -> ColorPreferenceDialogFragmentCompat.this.onColorSelected(UIColors.COLOR_LIGHT_TRANSPARENT));

        Button buttonColorTransparent = view.findViewById(R.id.colorTransparent);
        buttonColorTransparent.setOnClickListener(v -> ColorPreferenceDialogFragmentCompat.this.onColorSelected(UIColors.COLOR_TRANSPARENT));

        // show button for getting color from system if supported
        Button buttonColorSystem = view.findViewById(R.id.colorSystem);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buttonColorSystem.setVisibility(View.VISIBLE);
            buttonColorSystem.setOnClickListener(v -> ColorPreferenceDialogFragmentCompat.this.onColorSelected(UIColors.COLOR_SYSTEM));
        } else {
            buttonColorSystem.setVisibility(View.GONE);
        }

        // Set selected button style
        if (ColorPreferenceDialogFragmentCompat.this.selectedColor == UIColors.COLOR_DARK_TRANSPARENT) {
            selectButton(buttonColorTransparentDark);
        }
        if (ColorPreferenceDialogFragmentCompat.this.selectedColor == UIColors.COLOR_LIGHT_TRANSPARENT) {
            selectButton(buttonColorTransparentWhite);
        }
        if (ColorPreferenceDialogFragmentCompat.this.selectedColor == UIColors.COLOR_TRANSPARENT) {
            selectButton(buttonColorTransparent);
        }
        if (ColorPreferenceDialogFragmentCompat.this.selectedColor == UIColors.COLOR_SYSTEM) {
            selectButton(buttonColorSystem);
        }

        this.drawPalette();
    }

    protected void drawPalette() {
        if (this.palette != null) {
            this.palette.drawPalette(UIColors.getColorList(), this.selectedColor);
        }
    }

    @Override
    public void onColorSelected(@ColorInt int color) {
        if (color != this.selectedColor) {
            this.selectedColor = color;

            // Redraw palette to show checkmark on newly selected color before dismissing
            this.drawPalette();
        }

        // Close the dialog
        getDialog().dismiss();
    }

    private void selectButton(Button button) {
        Context context = requireContext();
        TypedValue tv = new TypedValue();
        boolean found = context.getTheme().resolveAttribute(android.R.attr.textColor, tv, true);
        @ColorInt int primaryColor = found ? tv.data : Color.BLACK;

        button.setTypeface(null, Typeface.BOLD);
        button.setTextColor(primaryColor);
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            ColorPreferenceCompat preference = (ColorPreferenceCompat) getPreference();
            if (preference.callChangeListener(selectedColor)) {
                preference.setSelectedColor(selectedColor);
            }
        }
    }
}
