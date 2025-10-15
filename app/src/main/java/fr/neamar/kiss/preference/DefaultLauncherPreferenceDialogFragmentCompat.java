package fr.neamar.kiss.preference;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.preference.PreferenceDialogFragmentCompat;

import fr.neamar.kiss.DummyActivity;

/**
 * Dialog fragment for DefaultLauncherPreferenceCompat.
 * Triggers the system's default launcher selection dialog.
 * 
 * The implementation uses a DummyActivity as a temporary HOME activity:
 * 1. Enable DummyActivity (disabled by default in manifest)
 * 2. Start ACTION_MAIN with CATEGORY_HOME intent
 * 3. Android shows launcher selection dialog
 * 4. Disable DummyActivity again
 * 
 * Migration from legacy onClick() to DialogFragmentCompat pattern
 * as part of Phase 6 Step 4.
 */
public class DefaultLauncherPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    /**
     * Creates a new instance of the dialog fragment.
     * @param key The preference key
     * @return A new dialog fragment instance
     */
    public static DefaultLauncherPreferenceDialogFragmentCompat newInstance(String key) {
        DefaultLauncherPreferenceDialogFragmentCompat fragment = new DefaultLauncherPreferenceDialogFragmentCompat();
        Bundle args = new Bundle(1);
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            // Get context
            Context context = requireContext();

            // Get package manager
            PackageManager packageManager = context.getPackageManager();
            
            // Get DummyActivity component
            ComponentName componentName = new ComponentName(context, DummyActivity.class);
            
            // Enable DummyActivity (it starts disabled in the manifest.xml)
            packageManager.setComponentEnabledSetting(
                componentName, 
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 
                PackageManager.DONT_KILL_APP
            );

            // Create a new (implicit) intent with MAIN action
            Intent intent = new Intent(Intent.ACTION_MAIN);
            // Add HOME category to it - this triggers the launcher selection dialog
            intent.addCategory(Intent.CATEGORY_HOME);
            // Launch intent
            context.startActivity(intent);

            // Disable DummyActivity once again
            packageManager.setComponentEnabledSetting(
                componentName, 
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 
                PackageManager.DONT_KILL_APP
            );
        }
    }
}
