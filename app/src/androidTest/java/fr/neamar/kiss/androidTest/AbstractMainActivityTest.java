package fr.neamar.kiss.androidTest;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import android.content.Context;
import android.os.Build;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;

abstract class AbstractMainActivityTest {
    @Rule
    public ActivityScenarioRule<MainActivity> mActivityRule = new ActivityScenarioRule<>(MainActivity.class);

    protected ActivityScenario<MainActivity> scenario;
    protected MainActivity activity;

    @Before
    public void setUp() {
        scenario = mActivityRule.getScenario();
        scenario.onActivity(activity -> {
            this.activity = activity;
            
            // Grant READ_CONTACTS permission for Android M+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                getInstrumentation().getUiAutomation().executeShellCommand(
                        "pm grant " + activity.getPackageName()
                                + " android.permission.READ_CONTACTS");
            }

            // Initialize to default preferences
            Context context = activity.getApplicationContext();
            KissApplication.getApplication(activity).getDataHandler().clearHistory();
            assertThat(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit(), is(true));
            PreferenceManager.setDefaultValues(context, R.xml.preferences, true);

            // Remove lock screen - use modern API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true);
                activity.setTurnScreenOn(true);
            }
        });
    }
}
