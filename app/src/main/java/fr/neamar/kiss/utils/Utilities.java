package fr.neamar.kiss.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.AsyncTask;
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

    public static Utilities.AsyncRun runAsync(@NonNull AsyncRun.Run background, @Nullable AsyncRun.Run after) {
        return runAsync(background, after, getDefaultExecutor());
    }

    public static Utilities.AsyncRun runAsync(@NonNull AsyncRun.Run background, @Nullable AsyncRun.Run after, @NonNull Executor exec) {
        return (Utilities.AsyncRun) new Utilities.AsyncRun(background, after).executeOnExecutor(exec);
    }

    /**
     * Kotlin Coroutines 기반 비동기 실행 (신규 권장 방식)
     * 기존 runAsync와 동일한 인터페이스를 제공하되 Coroutines 사용
     * 
     * @param background 백그라운드에서 실행할 작업
     * @param after UI 스레드에서 실행할 콜백 (nullable)
     * @return Job 취소 가능한 작업 참조
     */
    @NonNull
    public static kotlinx.coroutines.Job runAsyncCoroutines(@NonNull AsyncRun.Run background, @Nullable AsyncRun.Run after) {
        return CoroutineUtils.runAsync(
            () -> background.run(null), // AsyncRun.Run의 task 파라미터는 null로 처리
            after != null ? () -> after.run(null) : null
        );
    }

    /**
     * Get default executor for async operations.
     * From android Q AsyncTask.THREAD_POOL_EXECUTOR supports fallback if queue gets too long.
     * Else we use more safe AsyncTask.SERIAL_EXECUTOR.
     *
     * @return Executor
     */
    public static Executor getDefaultExecutor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return AsyncTask.THREAD_POOL_EXECUTOR;
        } else {
            return AsyncTask.SERIAL_EXECUTOR;
        }
    }

    public static class AsyncRun extends AsyncTask<Void, Void, Void> {
        private final Run mBackground;
        private final Run mAfter;

        public interface Run {
            void run(@NonNull Utilities.AsyncRun task);
        }

        public AsyncRun(@NonNull Run background, @Nullable Run after) {
            super();
            mBackground = background;
            mAfter = after;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            mBackground.run(this);
            return null;
        }

        @Override
        protected void onCancelled(Void aVoid) {
            if (mAfter != null)
                mAfter.run(this);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (mAfter != null)
                mAfter.run(this);
        }

        public boolean cancel() {
            return cancel(false);
        }
    }
}
