package fr.neamar.kiss.loader

import android.content.Context
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.UserManager
import androidx.annotation.WorkerThread
import fr.neamar.kiss.DataHandler
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.TagsHandler
import fr.neamar.kiss.db.DBHelper
import fr.neamar.kiss.db.ShortcutRecord
import fr.neamar.kiss.pojo.ShortcutPojo
import fr.neamar.kiss.utils.PackageManagerUtils
import fr.neamar.kiss.utils.ShortcutUtil
import fr.neamar.kiss.utils.UserHandle

/**
 * Kotlin Coroutines replacement for LoadShortcutsPojos AsyncTask
 * Loads shortcut POJOs from both database and system (Android O+)
 */
class LoadShortcutsPojosCoroutine(context: Context) : LoadPojosCoroutine<ShortcutPojo>(context, ShortcutPojo.SCHEME) {
    
    companion object {
        private const val TAG = "LoadShortcutsPojosCoroutine"
    }
    
    @WorkerThread
    override fun doInBackground(): List<ShortcutPojo> {
        val context = contextRef.get() ?: return emptyList()
        
        val nonOreoPojos = fetchNonOreoPojos(context)
        val oreoPojos = fetchOreoPojos(context)
        
        return mutableListOf<ShortcutPojo>().apply {
            addAll(nonOreoPojos)
            addAll(oreoPojos)
        }
    }
    
    /**
     * Get all Oreo shortcuts from system directly (Android O+)
     */
    @WorkerThread
    private fun fetchOreoPojos(context: Context): List<ShortcutPojo> {
        val oreoPojos = mutableListOf<ShortcutPojo>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dataHandler = KissApplication.getApplication(context).dataHandler
            val excludedApps = dataHandler.excluded
            val excludedShortcutApps = dataHandler.excludedShortcutApps
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val shortcutInfos = ShortcutUtil.getAllShortcuts(context)
            
            for (shortcutInfo in shortcutInfos) {
                try {
                    if (ShortcutUtil.isShortcutVisible(context, shortcutInfo, excludedApps, excludedShortcutApps)) {
                        val shortcutRecord = ShortcutUtil.createShortcutRecord(
                            context,
                            shortcutInfo,
                            !shortcutInfo.isPinned
                        )
                        
                        if (shortcutRecord != null) {
                            val isSuspended = PackageManagerUtils.isAppSuspended(
                                context,
                                shortcutInfo.`package`,
                                UserHandle(context, shortcutInfo.userHandle)
                            )
                            val isQuietModeEnabled = userManager.isQuietModeEnabled(shortcutInfo.userHandle)
                            val disabled = isSuspended || isQuietModeEnabled
                            
                            val pojo = createPojo(
                                shortcutRecord,
                                dataHandler.tagsHandler,
                                ShortcutUtil.getComponentName(context, shortcutInfo),
                                shortcutInfo.isPinned,
                                shortcutInfo.isDynamic,
                                disabled
                            )
                            
                            oreoPojos.add(pojo)
                        }
                    }
                } catch (e: Exception) {
                    // Continue processing other shortcuts if one fails
                    android.util.Log.w(TAG, "Error processing shortcut ${shortcutInfo.id}: ${e.message}")
                }
            }
        }
        
        return oreoPojos
    }
    
    /**
     * Get shortcuts from database (pre-Oreo and custom shortcuts)
     */
    @WorkerThread
    private fun fetchNonOreoPojos(context: Context): List<ShortcutPojo> {
        val dataHandler = KissApplication.getApplication(context).dataHandler
        val tagsHandler = dataHandler.tagsHandler
        val pojos = mutableListOf<ShortcutPojo>()
        val records = DBHelper.getShortcuts(context)
        
        for (shortcutRecord in records) {
            try {
                val pojo = createPojo(shortcutRecord, tagsHandler, null, true, false, false)
                if (!pojo.isOreoShortcut) {
                    // Add older shortcuts from DB
                    pojos.add(pojo)
                }
            } catch (e: Exception) {
                // Continue processing other shortcuts if one fails
                android.util.Log.w(TAG, "Error processing shortcut record ${shortcutRecord.name}: ${e.message}")
            }
        }
        
        return pojos
    }
    
    private fun createPojo(
        shortcutRecord: ShortcutRecord,
        tagsHandler: TagsHandler,
        componentName: String?,
        pinned: Boolean,
        dynamic: Boolean,
        disabled: Boolean
    ): ShortcutPojo {
        val pojo = ShortcutPojo(shortcutRecord, componentName, pinned, dynamic, disabled)
        pojo.name = shortcutRecord.name
        pojo.tags = tagsHandler.getTags(pojo.id)
        return pojo
    }
}
