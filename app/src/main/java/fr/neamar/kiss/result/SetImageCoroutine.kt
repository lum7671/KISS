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
        // Legacy AsyncTask handling is no longer needed - all converted to Coroutines
    }
    
    /**
     * Background task: Load drawable from Result
     * Returns null if operation should be cancelled
     */
    private fun loadDrawable(
        imageViewRef: WeakReference<ImageView>,
        resultRef: WeakReference<Result<*>>
    ): Drawable? {
        // Get references first and keep strong references during loading
        val imageView = imageViewRef.get() ?: return null
        val result = resultRef.get() ?: return null
        
        // Verify that our operation is still current (not replaced by another)
        val currentTag = imageView.tag
        if (currentTag !is ImageLoadingTag || currentTag.result != result) {
            return null
        }
        
        return try {
            // Load drawable with error handling and retry logic
            var drawable = result.getDrawable(imageView.context)
            
            // 아이콘이 null이면 여러 번 재시도
            var retryCount = 0
            while (drawable == null && retryCount < 3) {
                retryCount++
                Thread.sleep((100 * retryCount).toLong()) // 점진적 지연
                drawable = result.getDrawable(imageView.context)
                android.util.Log.w("SetImageCoroutine", "Retrying icon load (${retryCount}/3) for ${result.javaClass.simpleName}")
            }
            
            drawable
        } catch (e: Exception) {
            // 오류 발생 시 로그 남기고 null 반환
            android.util.Log.w("SetImageCoroutine", "Failed to load drawable: ${e.message}")
            null
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
        val result = resultRef.get() ?: return
        
        // Verify operation is still current
        val currentTag = imageView.tag
        if (currentTag !is ImageLoadingTag || currentTag.result != result) {
            return
        }
        
        // drawable이 null이어도 처리 - 기본 아이콘이라도 보여주기
        if (drawable != null) {
            // 정상적으로 로드된 경우
            imageView.setImageDrawable(drawable)
            imageView.tag = result // Restore original Result tag
        } else {
            // drawable이 null인 경우 - 기본 아이콘을 강제로 다시 로드
            android.util.Log.w("SetImageCoroutine", "Drawable is null, forcing default icon load")
            try {
                val defaultDrawable = result.getDrawable(imageView.context)
                if (defaultDrawable != null) {
                    imageView.setImageDrawable(defaultDrawable)
                    imageView.tag = result
                } else {
                    // 최후의 수단: 시스템 기본 아이콘
                    val systemDefault = imageView.context.resources.getDrawable(android.R.drawable.sym_def_app_icon)
                    imageView.setImageDrawable(systemDefault)
                    imageView.tag = result
                }
            } catch (e: Exception) {
                android.util.Log.e("SetImageCoroutine", "Failed to set fallback icon", e)
                // 그래도 tag는 설정해서 무한 로딩 방지
                imageView.tag = result
            }
        }
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
