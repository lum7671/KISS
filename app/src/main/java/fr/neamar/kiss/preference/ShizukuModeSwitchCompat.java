package fr.neamar.kiss.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.util.AttributeSet;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * AndroidX-based ShizukuModeSwitch preference.
 * Handles Shizuku availability check, permission request, and RootHandler reset.
 * 
 * Migration from android.preference.SwitchPreference to androidx.preference.SwitchPreferenceCompat
 * as part of Phase 6 Step 2.
 */
public class ShizukuModeSwitchCompat extends SwitchPreferenceCompat {
    
    public ShizukuModeSwitchCompat(Context context) {
        this(context, null);
    }

    public ShizukuModeSwitchCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.switchPreferenceCompatStyle);
    }

    public ShizukuModeSwitchCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ShizukuModeSwitchCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onClick() {
        if (!isChecked()) {
            // Trying to enable Shizuku mode
            // First refresh Shizuku status
            KissApplication.getApplication(getContext()).getRootHandler().refreshShizukuStatus();
            
            if (!KissApplication.getApplication(getContext()).getRootHandler().isShizukuAvailable()) {
                // Shizuku not available, show error dialog
                new AlertDialog.Builder(getContext())
                    .setMessage(R.string.shizuku_mode_error)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // User acknowledged, do nothing
                        }
                    })
                    .show();
                return;
                
            } else if (!KissApplication.getApplication(getContext()).getRootHandler().hasShizukuPermission()) {
                // Shizuku available but no permission, request permission
                KissApplication.getApplication(getContext()).getRootHandler().requestShizukuPermission();
                
                new AlertDialog.Builder(getContext())
                    .setMessage(R.string.shizuku_permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Wait 1 second for permission grant, then check again
                            new Handler(getContext().getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // Refresh status and check permission again
                                    KissApplication.getApplication(getContext()).getRootHandler().refreshShizukuStatus();
                                    if (KissApplication.getApplication(getContext()).getRootHandler().hasShizukuPermission()) {
                                        // Permission granted, enable the switch
                                        ShizukuModeSwitchCompat.super.onClick();
                                    } else {
                                        // Permission denied, show error
                                        new AlertDialog.Builder(getContext())
                                            .setMessage(R.string.shizuku_permission_denied)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                    }
                                }
                            }, 1000); // Wait 1 second
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return;
            }
        }
        
        // All checks passed or disabling, proceed with toggle
        super.onClick();

        // Reset RootHandler to apply new setting
        try {
            KissApplication.getApplication(getContext()).resetRootHandler(getContext());
        } catch (NullPointerException e) {
            // RootHandler not initialized yet, ignore
        }
    }
}
