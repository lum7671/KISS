package fr.neamar.kiss.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

public class Utilities {

    /**
     * Return a valid activity or null given a view
     *
     * @param view any view of an activity
     * @return an activity or null
     */
    @Nullable
    public static Activity getActivity(@Nullable View view) {
        return view != null ? getActivity(view.getContext()) : null;
    }

    /**
     * Return a valid activity or null given a context
     *
     * @param ctx context
     * @return an activity or null
     */
    @Nullable
    public static Activity getActivity(@Nullable Context ctx) {
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                Activity act = (Activity) ctx;
                if (act.isFinishing()) {
                    return null;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    if (act.isDestroyed()) {
                        return null;
                    }
                }
                return act;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }

    /**
     * Get default executor for async operations.
     * Note: This is kept for compatibility but Coroutines are now preferred.
     * 
     * @return Executor
     * @deprecated Use CoroutineUtils instead
     */
    @Deprecated
    public static java.util.concurrent.Executor getDefaultExecutor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return java.util.concurrent.ForkJoinPool.commonPool();
        } else {
            return java.util.concurrent.Executors.newSingleThreadExecutor();
        }
    }
}
