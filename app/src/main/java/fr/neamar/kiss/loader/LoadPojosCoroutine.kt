package fr.neamar.kiss.loader

import android.content.Context
import androidx.annotation.WorkerThread
import fr.neamar.kiss.dataprovider.Provider
import fr.neamar.kiss.pojo.Pojo
import fr.neamar.kiss.utils.CoroutineUtils
import kotlinx.coroutines.Job
import java.lang.ref.WeakReference

/**
 * Kotlin Coroutines replacement for LoadPojos AsyncTask
 * Provides type-safe, memory-efficient background loading of POJO data
 * 
 * @param T The type of Pojo to load
 */
abstract class LoadPojosCoroutine<T : Pojo>(
    context: Context,
    protected val pojoScheme: String = "(none)://"
) {
    
    protected val contextRef = WeakReference(context)
    private var providerRef: WeakReference<Provider<T>>? = null
    
    /**
     * Set the provider that will receive the loaded results
     */
    fun setProvider(provider: Provider<T>) {
        this.providerRef = WeakReference(provider)
    }
    
    /**
     * Get the scheme used for this POJO type
     */
    fun getScheme(): String = pojoScheme
    
    /**
     * Start the background loading operation
     * 
     * @return Job for cancellation control
     */
    fun executeAsync(): Job {
        return CoroutineUtils.runAsyncWithResult(
            background = object : fr.neamar.kiss.utils.AsyncCallable<List<T>> {
                override fun call(): List<T> {
                    return doInBackground()
                }
            },
            callback = object : fr.neamar.kiss.utils.AsyncCallback<List<T>> {
                override fun onResult(result: List<T>) {
                    onPostExecute(result)
                }
                
                override fun onError(error: Exception) {
                    // Log error and provide empty list to maintain app stability
                    android.util.Log.e("LoadPojosCoroutine", "Error loading ${pojoScheme} data", error)
                    onPostExecute(emptyList())
                }
            }
        )
    }
    
    /**
     * Background work - override this method to implement data loading
     * This runs on a background thread
     * 
     * @return List of loaded POJOs
     */
    @WorkerThread
    protected abstract fun doInBackground(): List<T>
    
    /**
     * UI thread callback - called when background work completes
     * This runs on the main thread
     * 
     * @param result The loaded POJOs
     */
    private fun onPostExecute(result: List<T>) {
        val provider = providerRef?.get()
        if (provider != null) {
            provider.loadOver(result)
        }
    }
    
    companion object {
        private const val TAG = "LoadPojosCoroutine"
    }
}
