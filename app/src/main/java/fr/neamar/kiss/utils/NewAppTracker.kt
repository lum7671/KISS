package fr.neamar.kiss.utils

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Tracks whether an app has been shown in History at least once.
 * SharedPreferences-based, no DB change required.
 */
class NewAppTracker(private val context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val keySeen = "seen_in_history"

    private fun getSeenSet(): MutableSet<String> {
        val stored = prefs.getStringSet(keySeen, emptySet()) ?: emptySet()
        return stored.toMutableSet()
    }

    fun isNewApp(appId: String?): Boolean {
        if (appId.isNullOrEmpty()) return false
        val seen = prefs.getStringSet(keySeen, emptySet()) ?: emptySet()
        return !seen.contains(appId)
    }

    fun markAsSeen(appId: String?) {
        if (appId.isNullOrEmpty()) return
        val seen = getSeenSet()
        if (seen.add(appId)) {
            prefs.edit().putStringSet(keySeen, seen).apply()
        }
    }

    fun removeApp(appId: String?) {
        if (appId.isNullOrEmpty()) return
        val seen = getSeenSet()
        if (seen.remove(appId)) {
            prefs.edit().putStringSet(keySeen, seen).apply()
        }
    }

    fun removePackage(packageName: String?) {
        if (packageName.isNullOrEmpty()) return
        val seen = getSeenSet()
        val prefix = "app://$packageName/"
        val removed = seen.removeAll { it.startsWith(prefix) }
        if (removed) {
            prefs.edit().putStringSet(keySeen, seen).apply()
        }
    }

    fun clearAll() {
        prefs.edit().remove(keySeen).apply()
    }
}
