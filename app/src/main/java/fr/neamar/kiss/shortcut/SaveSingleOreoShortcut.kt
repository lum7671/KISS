package fr.neamar.kiss.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import fr.neamar.kiss.DataHandler
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.R
import fr.neamar.kiss.utils.CoroutineUtils
import fr.neamar.kiss.utils.AsyncCallable
import fr.neamar.kiss.utils.AsyncCallback
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

@RequiresApi(Build.VERSION_CODES.O)
class SaveSingleOreoShortcut private constructor(
    context: Context,
    private val intent: Intent
) {
    companion object {
        private const val TAG = "SaveSingleOreoShortcut"
        
        /**
         * AsyncTask의 execute()를 대체하는 정적 메서드
         * 기존 코드와의 호환성을 위해 동일한 인터페이스 제공
         */
        @JvmStatic
        fun execute(context: Context, intent: Intent): Job {
            val instance = SaveSingleOreoShortcut(context, intent)
            return instance.executeAsync()
        }
    }
    
    private val contextRef = WeakReference(context)
    
    /**
     * 비동기 실행 메서드
     * 기존 AsyncTask의 doInBackground + onPostExecute 패턴을 구현
     */
    private fun executeAsync(): Job {
        return CoroutineUtils.runAsyncWithResult(
            object : AsyncCallable<Boolean> {
                override fun call(): Boolean = doInBackground()
            },
            object : AsyncCallback<Boolean> {
                override fun onResult(result: Boolean) {
                    onPostExecute(result)
                }
                
                override fun onError(error: Exception) {
                    Log.e(TAG, "Error saving shortcut", error)
                    showErrorToast()
                }
            }
        )
    }
    
    /**
     * 백그라운드 작업 (기존 doInBackground와 동일한 로직)
     */
    private fun doInBackground(): Boolean {
        @Suppress("DEPRECATION")
        val pinItemRequest = intent.getParcelableExtra<LauncherApps.PinItemRequest>(LauncherApps.EXTRA_PIN_ITEM_REQUEST)
        val shortcutInfo = pinItemRequest?.shortcutInfo
        
        if (shortcutInfo == null) {
            throw IllegalArgumentException("ShortcutInfo is null")
        }
        
        if (!pinItemRequest.isValid) {
            return false
        }
        
        if (!pinItemRequest.accept()) {
            return false
        }
        
        val context = contextRef.get() 
            ?: throw IllegalStateException("Context is null")
        
        val dataHandler = KissApplication.getApplication(context).dataHandler
        
        // Add shortcut to the DataHandler
        return dataHandler.updateShortcut(shortcutInfo, false)
    }
    
    /**
     * UI 업데이트 (기존 onPostExecute와 동일한 로직)
     */
    private fun onPostExecute(success: Boolean) {
        if (success) {
            Log.i(TAG, "Shortcut added to KISS")
            
            val context = contextRef.get()
            if (context != null) {
                KissApplication.getApplication(context).dataHandler.reloadShortcuts()
            }
        }
    }
    
    /**
     * 에러 발생 시 토스트 표시
     */
    private fun showErrorToast() {
        val context = contextRef.get()
        if (context != null) {
            Toast.makeText(context, R.string.cant_pin_shortcut, Toast.LENGTH_LONG).show()
        }
    }
}
