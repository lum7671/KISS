package fr.neamar.kiss.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.AttributeSet;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * AndroidX-based RootModeSwitch preference.
 * Checks root availability before enabling root mode and resets RootHandler when toggled.
 * 
 * Migration from android.preference.SwitchPreference to androidx.preference.SwitchPreferenceCompat
 * as part of Phase 6 Step 2.
 */
public class RootModeSwitchCompat extends SwitchPreferenceCompat {
    
    public RootModeSwitchCompat(Context context) {
        this(context, null);
    }

    public RootModeSwitchCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.switchPreferenceCompatStyle);
    }

    public RootModeSwitchCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public RootModeSwitchCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onClick() {
        // Check root availability when enabling root mode
        if (!isChecked() && !KissApplication.getApplication(getContext()).getRootHandler().isRootAvailable()) {
            // Root not available, show error dialog
            new AlertDialog.Builder(getContext())
                    .setMessage(R.string.root_mode_error)
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // User acknowledged, do nothing
                        }
                    })
                    .show();
        } else {
            // Root available or disabling, proceed with toggle
            super.onClick();
        }

        // Reset RootHandler to apply new setting
        try {
            KissApplication.getApplication(getContext()).resetRootHandler(getContext());
        } catch (NullPointerException e) {
            // RootHandler not initialized yet, ignore
        }
    }
}
