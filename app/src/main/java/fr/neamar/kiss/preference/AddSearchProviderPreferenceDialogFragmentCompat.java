package fr.neamar.kiss.preference;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StyleableRes;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

import fr.neamar.kiss.R;
import fr.neamar.kiss.dataprovider.simpleprovider.SearchProvider;
import fr.neamar.kiss.utils.URIUtils;
import fr.neamar.kiss.utils.URLUtils;

/**
 * Dialog fragment for AddSearchProviderPreferenceCompat.
 * Handles custom search provider addition with comprehensive validation.
 */
public class AddSearchProviderPreferenceDialogFragmentCompat extends PreferenceDialogFragmentCompat {

    private EditText providerName;
    private EditText providerUri;
    private SharedPreferences prefs;

    public static AddSearchProviderPreferenceDialogFragmentCompat newInstance(String key) {
        AddSearchProviderPreferenceDialogFragmentCompat fragment = new AddSearchProviderPreferenceDialogFragmentCompat();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    protected View onCreateDialogView(@NonNull Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Create layout programmatically
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        providerName = new EditText(context);
        providerUri = new EditText(context);

        providerName.setHint(R.string.search_provider_name);
        providerUri.setHint(R.string.search_provider_url);
        providerUri.setInputType(InputType.TYPE_TEXT_VARIATION_URI);

        providerName.setText("");
        providerUri.setText("");

        // Adding margins (default is zero)
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(40, 10, 40, 0);
        layoutParams.setMarginStart(40);
        layoutParams.setMarginEnd(40);

        // Add the two text fields (with margins)
        layout.addView(providerName, layoutParams);
        layout.addView(providerUri, layoutParams);

        // Default text color is white that doesn't work well on the light themes
        String theme = prefs.getString("theme", "light");
        // If theme is light, change the text color
        if (!theme.contains("dark")) {
            @SuppressLint("ResourceType")
            @StyleableRes int[] attrs = {android.R.attr.textColor};
            TypedArray ta = context.obtainStyledAttributes(R.style.AppThemeLight, attrs);

            providerName.setTextColor(ta.getColor(0, Color.TRANSPARENT));
            providerUri.setTextColor(ta.getColor(0, Color.TRANSPARENT));
            ta.recycle();
        }

        return layout;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Override positive button to prevent automatic dismissal
        final AlertDialog dlg = (AlertDialog) getDialog();
        if (dlg != null) {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (validate()) {
                    save();
                    dlg.dismiss();
                }
            });
        }
    }

    private boolean validatePipes() {
        return !(providerName.getText().toString().contains("|") || providerUri.getText().toString().contains("|"));
    }

    private boolean isPlaceholder() {
        return providerUri.getText().toString().equals("%s");
    }

    private boolean validateQueryPlaceholder() {
        return providerUri.getText().toString().contains("%s");
    }

    @SuppressWarnings("StringSplitter")
    private boolean validateNameExists() {
        Set<String> availableSearchProviders = prefs.getStringSet("available-search-providers", SearchProvider.getDefaultSearchProviders(requireContext()));
        for (String searchProvider : availableSearchProviders) {
            String[] nameAndUrl = searchProvider.split("\\|");
            if (nameAndUrl.length == 2) {
                if (nameAndUrl[0].equals(providerName.getText().toString())) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validateEmpty() {
        return !TextUtils.isEmpty(providerName.getText()) && !TextUtils.isEmpty(providerUri.getText());
    }

    private boolean validateUrl() {
        return URLUtils.matchesUrlPattern(providerUri.getText().toString());
    }

    private URIUtils.URIValidity validateUri() {
        return URIUtils.isValidUri(providerUri.getText().toString(), requireContext());
    }

    private boolean validate() {
        if (!validateEmpty()) {
            // do not close - empty strings
            return false;
        }
        // check if input contains |
        if (!validatePipes()) {
            //show tip
            Toast.makeText(requireContext(), R.string.search_provider_error_char, Toast.LENGTH_SHORT).show();
            //cancel close dialog
            return false;
        }
        //check if custom provider
        if (!validateNameExists()) {
            //show tip
            Toast.makeText(requireContext(), R.string.search_provider_error_exists, Toast.LENGTH_SHORT).show();
            //cancel close dialog
            return false;
        }
        // check input
        if (!validateQueryPlaceholder()) {
            //show tip
            Toast.makeText(requireContext(), R.string.search_provider_error_placeholder, Toast.LENGTH_SHORT).show();
            //cancel close dialog
            return false;
        }
        //if all validates are correct, then close dialog with close flag = true

        // placeholder alone is valid too
        if (isPlaceholder()) {
            return true;
        }

        // If provider submitted is submitted not more check is need
        if (validateUrl()) {
            return true;
        }

        //check if a valid uri is given instead valid url
        final URIUtils.URIValidity uriResult = validateUri();
        if (uriResult.isValid) {
            return true;
        }

        switch (uriResult) {
            case NOT_AN_URI:
                Toast.makeText(requireContext(), R.string.search_provider_error_url, Toast.LENGTH_SHORT).show();
                //not an uri and not an url
                return false;
            case NO_APP_CAN_HANDLE_URI:
                Toast.makeText(requireContext(), R.string.search_provider_error_uri_cannot_be_handle, Toast.LENGTH_SHORT).show();
                //valid uri but no app can handle this intent
                return false;
            default:
                Log.w(this.getClass().getCanonicalName(),"validate: Following error case for uriResult unmanaged : " + uriResult);
                return false;
        }
    }

    //persist values and disassemble views
    protected void save() {
        Set<String> availableProviders = new HashSet<>(prefs.getStringSet("available-search-providers", SearchProvider.getDefaultSearchProviders(requireContext())));
        availableProviders.add(providerName.getText().toString() + "|" + providerUri.getText().toString());
        prefs.edit().putStringSet("available-search-providers", availableProviders).apply();
        prefs.edit().putStringSet("deleting-search-providers-names", availableProviders).apply();

        Toast.makeText(requireContext(), R.string.search_provider_added, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        // All logic handled in validate() and save() methods
        // No need to do anything here since we control dismissal in onStart()
    }
}
