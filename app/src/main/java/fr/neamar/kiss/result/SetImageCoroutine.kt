package fr.neamar.kiss.result

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import fr.neamar.kiss.utils.CoroutineUtils
import kotlinx.coroutines.Job
import java.lang.ref.WeakReference

/**
 * Kotlin Coroutines replacement for AsyncSetImage
 * Provides memory-safe image loading with automatic cancellation and WeakReference management
 */
object SetImageCoroutine {
    
    /**
     * Coroutines-based image loading with WeakReference and tag-based cancellation
     * 
     * @param imageView Target ImageView
     * @param result Result object providing the drawable
     * @param resId Resource ID for temporary placeholder
     * @return Job for cancellation control
     */
    fun setImageAsync(
        imageView: ImageView,
        result: Result<*>,
        @DrawableRes resId: Int
    ): Job {
        // Check if we're already loading this specific result
        val currentTag = imageView.tag
        if (currentTag is ImageLoadingTag && currentTag.result == result) {
            // Already loading the same result, return existing job
            return currentTag.job
        }
        
        // Cancel any existing operation for this ImageView
        cancelPendingOperation(imageView)
        
        // Set initial placeholder image
        imageView.setImageResource(resId)
        
        // Create weak references for memory safety
        val imageViewRef = WeakReference(imageView)
        val resultRef = WeakReference(result)
        
        // Start the coroutine-based operation
        val job = CoroutineUtils.runAsyncWithResult(
            background = object : fr.neamar.kiss.utils.AsyncCallable<Drawable?> {
                override fun call(): Drawable? {
                    return loadDrawable(imageViewRef, resultRef)
                }
            },
            callback = object : fr.neamar.kiss.utils.AsyncCallback<Drawable?> {
                override fun onResult(result: Drawable?) {
                    applyDrawable(imageViewRef, resultRef, result)
                }
                
                override fun onError(error: Exception) {
                    // Handle error gracefully - just clear the weak references
                    imageViewRef.clear()
                    resultRef.clear()
                }
            }
        )
        
        // Tag the ImageView with our operation info for cancellation tracking
        imageView.tag = ImageLoadingTag(job, result)
        
        return job
    }
    
    /**
     * Cancel any pending image loading operation for the given ImageView
     */
    fun cancelPendingOperation(imageView: ImageView) {
        val currentTag = imageView.tag
        
        // Handle Coroutine-based operations
        if (currentTag is ImageLoadingTag) {
            currentTag.job.cancel()
            imageView.tag = null
        }
        // Handle legacy AsyncSetImage operations
        else if (currentTag is fr.neamar.kiss.result.Result.AsyncSetImage) {
            currentTag.cancel(true)
            imageView.tag = null
        }
    }
    
    /**
     * Background task: Load drawable from Result
     * Returns null if operation should be cancelled
     */
    private fun loadDrawable(
        imageViewRef: WeakReference<ImageView>,
        resultRef: WeakReference<Result<*>>
    ): Drawable? {
        // Check if ImageView is still valid
        val imageView = imageViewRef.get() ?: return null
        
        // Verify that our operation is still current (not replaced by another)
        val currentTag = imageView.tag
        if (currentTag !is ImageLoadingTag || currentTag.result != resultRef.get()) {
            return null
        }
        
        // Get the result and load drawable
        val result = resultRef.get() ?: return null
        return try {
            result.getDrawable(imageView.context)
        } catch (e: Exception) {
            null // Handle any loading errors gracefully
        }
    }
    
    /**
     * UI Thread task: Apply the loaded drawable to ImageView
     */
    private fun applyDrawable(
        imageViewRef: WeakReference<ImageView>,
        resultRef: WeakReference<Result<*>>,
        drawable: Drawable?
    ) {
        val imageView = imageViewRef.get() ?: return
        
        // Verify operation is still current and drawable is valid
        val currentTag = imageView.tag
        if (currentTag !is ImageLoadingTag || 
            currentTag.result != resultRef.get() || 
            drawable == null) {
            return
        }
        
        // Set the loaded drawable and restore the Result tag
        imageView.setImageDrawable(drawable)
        imageView.tag = resultRef.get() // Restore original Result tag
    }
    
    /**
     * Tag class for tracking active image loading operations
     * Used to prevent race conditions and enable proper cancellation
     */
    data class ImageLoadingTag(
        val job: Job,
        val result: Result<*>
    )
}
