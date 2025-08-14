package fr.neamar.kiss.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import fr.neamar.kiss.BuildConfig

/**
 * Kotlin Coroutines 기반 비동기 실행 유틸리티
 * 기존 AsyncTask.execute() 및 Utilities.AsyncRun을 대체
 */
object CoroutineUtils {
    
    /**
     * 애플리케이션 레벨 CoroutineScope
     * GlobalScope 대신 사용하여 더 나은 구조화된 동시성 제공
     */
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineName("CoroutineUtils")
    )
    
    /**
     * 메인 스레드에서 실행하기 위한 Handler
     */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /**
     * 기존 AsyncTask.execute() 대체용 간단한 비동기 실행
     * 
     * @param background 백그라운드에서 실행할 작업
     */
    @JvmStatic
    fun execute(background: Runnable) {
        if (BuildConfig.DEBUG) {
            Log.d("CoroutineUtils", "execute() called")
        }
        applicationScope.launch {
            if (BuildConfig.DEBUG) {
                Log.d("CoroutineUtils", "execute() - background task started")
            }
            background.run()
            if (BuildConfig.DEBUG) {
                Log.d("CoroutineUtils", "execute() - background task completed")
            }
        }
    }
    
    /**
     * 백그라운드 작업 + UI 콜백 패턴 (기존 Utilities.AsyncRun 대체)
     * 
     * @param background 백그라운드에서 실행할 작업
     * @param callback UI 스레드에서 실행할 콜백 (nullable)
     */
    @JvmStatic
    fun runAsync(
        @NonNull background: AsyncRunnable,
        callback: AsyncRunnable? = null
    ): Job {
        if (BuildConfig.DEBUG) {
            Log.d("CoroutineUtils", "runAsync() called")
        }
        return applicationScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                    Log.d("CoroutineUtils", "runAsync() - background task started")
                }
                background.run()
                if (BuildConfig.DEBUG) {
                    Log.d("CoroutineUtils", "runAsync() - background task completed")
                }
                
                callback?.let {
                    if (BuildConfig.DEBUG) {
                        Log.d("CoroutineUtils", "runAsync() - UI callback started")
                    }
                    withContext(Dispatchers.Main) {
                        it.run()
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d("CoroutineUtils", "runAsync() - UI callback completed")
                    }
                }
            } catch (e: Exception) {
                Log.e("CoroutineUtils", "runAsync() - error occurred", e)
                e.printStackTrace()
            }
        }
    }
    
    /**
     * LifecycleOwner와 연결된 안전한 비동기 실행
     * Activity/Fragment가 destroy되면 자동으로 취소됨
     * 
     * @param lifecycleOwner Activity 또는 Fragment
     * @param background 백그라운드에서 실행할 작업
     * @param callback UI 스레드에서 실행할 콜백 (nullable)
     */
    @JvmStatic
    fun runAsyncWithLifecycle(
        lifecycleOwner: LifecycleOwner,
        @NonNull background: AsyncRunnable,
        @Nullable callback: AsyncRunnable? = null
    ): Job {
        return lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                background.run()
                
                callback?.let {
                    withContext(Dispatchers.Main) {
                        it.run()
                    }
                }
            } catch (e: CancellationException) {
                // Lifecycle이 종료되어 취소된 경우는 정상적인 상황
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 제네릭 타입을 지원하는 비동기 작업 실행
     * 
     * @param T 결과 타입
     * @param background 백그라운드에서 실행할 작업 (결과 반환)
     * @param callback UI 스레드에서 실행할 콜백
     */
    @JvmStatic
    fun <T> runAsyncWithResult(
        @NonNull background: AsyncCallable<T>,
        @NonNull callback: AsyncCallback<T>
    ): Job {
        return applicationScope.launch {
            try {
                val result = background.call()
                
                withContext(Dispatchers.Main) {
                    callback.onResult(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e)
                }
            }
        }
    }
    
    /**
     * LifecycleOwner와 연결된 제네릭 타입 비동기 작업
     */
    @JvmStatic
    fun <T> runAsyncWithResultAndLifecycle(
        lifecycleOwner: LifecycleOwner,
        @NonNull background: AsyncCallable<T>,
        @NonNull callback: AsyncCallback<T>
    ): Job {
        return lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = background.call()
                
                withContext(Dispatchers.Main) {
                    callback.onResult(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e)
                }
            }
        }
    }
    
    /**
     * 기존 AsyncTask의 onPostExecute와 유사한 패턴
     * WeakReference를 사용하여 메모리 누수 방지
     */
    @JvmStatic
    fun <T, R> runAsyncWithWeakReference(
        target: T,
        @NonNull background: WeakAsyncCallable<T, R>,
        @NonNull callback: WeakAsyncCallback<T, R>
    ): Job where T : Any {
        val weakRef = WeakReference(target)
        
        return applicationScope.launch {
            try {
                val targetRef = weakRef.get()
                if (targetRef != null) {
                    val result = background.call(targetRef)
                    
                    withContext(Dispatchers.Main) {
                        val finalTarget = weakRef.get()
                        if (finalTarget != null) {
                            callback.onResult(finalTarget, result)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val targetRef = weakRef.get()
                    if (targetRef != null) {
                        callback.onError(targetRef, e)
                    }
                }
            }
        }
    }
    
    /**
     * 애플리케이션 종료 시 정리
     */
    @JvmStatic
    fun shutdown() {
        applicationScope.cancel()
    }
}

/**
 * 함수형 인터페이스들
 */
@FunctionalInterface
interface AsyncRunnable {
    fun run()
}

@FunctionalInterface
interface AsyncCallable<T> {
    fun call(): T
}

interface AsyncCallback<T> {
    fun onResult(result: T)
    fun onError(error: Exception)
}

@FunctionalInterface
interface WeakAsyncCallable<T, R> {
    fun call(target: T): R
}

interface WeakAsyncCallback<T, R> {
    fun onResult(target: T, result: R)
    fun onError(target: T, error: Exception)
}
