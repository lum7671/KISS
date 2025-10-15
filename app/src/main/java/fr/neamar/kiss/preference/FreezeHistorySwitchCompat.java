package fr.neamar.kiss.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.AttributeSet;

import fr.neamar.kiss.R;

/**
 * AndroidX-based FreezeHistorySwitch preference.
 * Shows a warning dialog when user tries to enable history freezing.
 * 
 * Migration from android.preference.SwitchPreference to androidx.preference.SwitchPreferenceCompat
 * as part of Phase 6 Step 2.
 */
public class FreezeHistorySwitchCompat extends SwitchPreferenceCompat {

    public FreezeHistorySwitchCompat(Context context) {
        this(context, null);
    }

    public FreezeHistorySwitchCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.switchPreferenceCompatStyle);
    }

    public FreezeHistorySwitchCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public FreezeHistorySwitchCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onClick() {
        if (!isChecked()) {
            // Show warning dialog when enabling history freeze
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.freeze_history_warn)
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FreezeHistorySwitchCompat.super.onClick();
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // User cancelled, do nothing
                        }
                    })
                    .show();
        } else {
            // Disabling freeze, no warning needed
            super.onClick();
        }
    }
}
