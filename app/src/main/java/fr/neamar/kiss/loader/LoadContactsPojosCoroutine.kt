package fr.neamar.kiss.loader

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.annotation.WorkerThread
import fr.neamar.kiss.pojo.ContactsPojo
import fr.neamar.kiss.utils.Permission

/**
 * Kotlin Coroutines replacement for LoadContactsPojos AsyncTask
 * Simplified version focusing on basic contact loading functionality
 */
class LoadContactsPojosCoroutine(context: Context) : LoadPojosCoroutine<ContactsPojo>(context, "contact://") {
    
    companion object {
        private const val TAG = "LoadContactsPojosCoroutine"
    }
    
    @WorkerThread
    override fun doInBackground(): List<ContactsPojo> {
        val start = System.currentTimeMillis()
        
        val contacts = mutableListOf<ContactsPojo>()
        val ctx = contextRef.get() ?: return contacts
        
        // Skip if we don't have permission to list contacts yet
        if (!Permission.checkPermission(ctx, Permission.PERMISSION_READ_CONTACTS)) {
            Log.w(TAG, "No permission to read contacts")
            return contacts
        }
        
        try {
            // Load basic contacts with phone numbers
            contacts.addAll(loadPhoneContacts(ctx))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading contacts", e)
        }
        
        val end = System.currentTimeMillis()
        Log.i(TAG, "${end - start} milliseconds to load ${contacts.size} contacts")
        
        return contacts
    }
    
    @WorkerThread
    private fun loadPhoneContacts(ctx: Context): List<ContactsPojo> {
        val contacts = mutableListOf<ContactsPojo>()
        
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val lookupIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
            val contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val displayNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val isPrimaryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            val isStarredIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
            
            while (cursor.moveToNext()) {
                try {
                    val contactId = cursor.getLong(contactIdIndex)
                    val lookupKey = cursor.getString(lookupIndex) ?: ""
                    val displayName = cursor.getString(displayNameIndex) ?: ""
                    val phone = cursor.getString(phoneIndex) ?: ""
                    val isPrimary = cursor.getInt(isPrimaryIndex) != 0
                    val isStarred = cursor.getInt(isStarredIndex) != 0
                    
                    if (phone.isNotEmpty() && displayName.isNotEmpty()) {
                        val id = "$pojoScheme$contactId/$phone"
                        val icon = Uri.EMPTY // Simplified - no icon for now
                        
                        val contact = ContactsPojo(id, lookupKey, contactId, icon, isPrimary, isStarred)
                        contact.setName(displayName)
                        contact.setPhone(phone, false)
                        
                        contacts.add(contact)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing contact: ${e.message}")
                }
            }
        }
        
        return contacts
    }
}
