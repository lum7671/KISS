package fr.neamar.kiss

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import fr.neamar.kiss.forwarder.ExperienceTweaks
import fr.neamar.kiss.forwarder.InterfaceTweaks
import fr.neamar.kiss.utils.Permission
import fr.neamar.kiss.utils.SystemUiVisibilityHelper

/**
 * Settings activity using Fragment-based architecture.
 * All preference logic is handled by SettingsFragment.
 * This activity serves as a container and handles navigation.
 */
class NewSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NewSettingsActivity"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var permissionManager: Permission
    private lateinit var systemUiVisibilityHelper: SystemUiVisibilityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Apply AppCompat-compatible theme based on user preference
        val theme = prefs.getString("theme", "light")
        when {
            theme == "amoled-dark" -> setTheme(R.style.NewSettingThemeDark) // Use dark for amoled too
            theme?.contains("dark") == true -> setTheme(R.style.NewSettingThemeDark)
            else -> setTheme(R.style.NewSettingTheme)
        }
        
        InterfaceTweaks.applySystemBarInsets(window.decorView)

        systemUiVisibilityHelper = SystemUiVisibilityHelper(this)
        ExperienceTweaks.setRequestedOrientation(this, prefs)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Setup ActionBar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // Load SettingsFragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        
        // Handle back stack changes to update ActionBar title
        supportFragmentManager.addOnBackStackChangedListener {
            val backStackEntryCount = supportFragmentManager.backStackEntryCount
            if (backStackEntryCount == 0) {
                supportActionBar?.setTitle(R.string.activity_setting)
            }
        }

        permissionManager = Permission(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_settings, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // Handle ActionBar up button - navigate back in fragment stack
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    // No back stack, finish activity
                    finish()
                }
                return true
            }
            R.id.help -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://help.kisslauncher.com"))
                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        systemUiVisibilityHelper.onWindowFocusChanged(hasFocus)
    }
}
