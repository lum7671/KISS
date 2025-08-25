package fr.neamar.kiss.shortcut

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.os.Build
import androidx.preference.PreferenceManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import fr.neamar.kiss.DataHandler
import fr.neamar.kiss.KissApplication
import fr.neamar.kiss.R
import fr.neamar.kiss.utils.ShortcutUtil
import fr.neamar.kiss.utils.CoroutineUtils
import fr.neamar.kiss.utils.AsyncCallable
import fr.neamar.kiss.utils.AsyncCallback
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

@RequiresApi(Build.VERSION_CODES.O)
class SaveAllOreoShortcuts private constructor(
    context: Context
) {
    companion object {
        private const val TAG = "SaveAllOreoShortcuts"
        
        /**
         * AsyncTask의 execute()를 대체하는 정적 메서드
         * 기존 코드와의 호환성을 위해 동일한 인터페이스 제공
         */
        @JvmStatic
        fun execute(context: Context): Job {
            val instance = SaveAllOreoShortcuts(context)
            return instance.executeAsync()
        }
    }
    
    private val contextRef = WeakReference(context)
    
    /**
     * 비동기 실행 메서드
     * 기존 AsyncTask의 doInBackground + onPostExecute + onProgressUpdate 패턴을 구현
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
                    Log.e(TAG, "Error saving all shortcuts", error)
                    when (error) {
                        is SecurityException -> showSecurityErrorToast()
                        else -> showGeneralErrorToast()
                    }
                }
            }
        )
    }
    
    /**
     * 백그라운드 작업 (기존 doInBackground와 동일한 로직)
     */
    private fun doInBackground(): Boolean {
        val context = contextRef.get() 
            ?: throw IllegalStateException("Context is null")
        
        val shortcuts: List<ShortcutInfo>
        try {
            // Fetch list of all shortcuts
            shortcuts = ShortcutUtil.getAllShortcuts(context)
        } catch (e: SecurityException) {
            Log.e(TAG, "Unable to get all shortcuts", e)
            
            // Set flag to true, so we can rerun this class
            @Suppress("DEPRECATION")
            val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putBoolean("first-run-shortcuts", true).apply()
            
            throw e // 예외를 다시 던져서 onError에서 처리
        }
        
        val dataHandler: DataHandler = KissApplication.getApplication(context).dataHandler
        
        var shortcutsUpdated = false
        for (shortcutInfo in shortcuts) {
            // add pinned shortcuts, remove disabled shortcuts
            if (shortcutInfo.isPinned || !shortcutInfo.isEnabled) {
                shortcutsUpdated = shortcutsUpdated or dataHandler.updateShortcut(shortcutInfo, !shortcutInfo.isPinned)
            }
        }
        
        return shortcutsUpdated
    }
    
    /**
     * UI 업데이트 (기존 onPostExecute와 동일한 로직)
     */
    private fun onPostExecute(success: Boolean) {
        if (success) {
            Log.i(TAG, "Shortcuts added to KISS")
            
            val context = contextRef.get()
            if (context != null) {
                KissApplication.getApplication(context).dataHandler.reloadShortcuts()
            }
        }
    }
    
    /**
     * 보안 에러 발생 시 토스트 표시 (기존 onProgressUpdate의 -1 케이스)
     */
    private fun showSecurityErrorToast() {
        val context = contextRef.get()
        if (context != null) {
            Toast.makeText(context, R.string.cant_pin_shortcut, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 일반 에러 발생 시 토스트 표시
     */
    private fun showGeneralErrorToast() {
        val context = contextRef.get()
        if (context != null) {
            Toast.makeText(context, "단축키 저장 중 오류가 발생했습니다", Toast.LENGTH_LONG).show()
        }
    }
}
