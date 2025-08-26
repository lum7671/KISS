package fr.neamar.kiss.searcher;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.CallSuper;
import com.amplitude.api.Amplitude;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.RelevanceComparator;
import fr.neamar.kiss.result.Result;

public abstract class Searcher implements Runnable {

    private static final String TAG = Searcher.class.getSimpleName();

    // define a different thread than the default AsyncTask thread or else we will block everything else that uses AsyncTask while we search
    public static final ExecutorService SEARCH_THREAD = Executors.newSingleThreadExecutor();
    static final int DEFAULT_MAX_RESULTS = 50;
    final WeakReference<MainActivity> activityWeakReference;
    private final PriorityQueue<Pojo> processedPojos;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile Future<?> task;
    private volatile boolean cancelled = false;
    private long start;
    /**
     * Set to true when we are simply refreshing current results (scroll will not be reset)
     * When false, we reset the scroll back to the last item in the list
     */
    private final boolean isRefresh;
    protected final String query;

    Searcher(MainActivity activity, String query, boolean isRefresh) {
        this.isRefresh = isRefresh;
        this.query = query == null ? null : query.trim();
        this.activityWeakReference = new WeakReference<>(activity);
        this.processedPojos = getPojoProcessor(activity);
    }

    PriorityQueue<Pojo> getPojoProcessor(Context context) {
        return new PriorityQueue<>(DEFAULT_MAX_RESULTS, new RelevanceComparator());
    }

    protected int getMaxResultCount() {
        return DEFAULT_MAX_RESULTS;
    }

    /**
     * Add single pojo to results.
     * This is called from the background thread by the providers.
     */
    public final boolean addResult(Pojo pojos) {
        return addResults(Collections.singletonList(pojos));
    }

    /**
     * Add one or more pojos to results.
     * This is called from the background thread by the providers.
     */
    public boolean addResults(List<? extends Pojo> pojos) {
        if (isCancelled())
            return false;

        return this.processedPojos.addAll(pojos);
    }

    @CallSuper
    protected void onPreExecute() {
        start = System.currentTimeMillis();
        displayActivityLoader();
    }

    protected void displayActivityLoader() {
        MainActivity activity = activityWeakReference.get();
        if (activity == null)
            return;

        activity.displayLoader(true);
    }

    private void hideActivityLoader(MainActivity activity) {
        // Loader should still be displayed until all the providers have finished loading
        activity.displayLoader(!KissApplication.getApplication(activity).getDataHandler().allProvidersHaveLoaded);
    }

    @Override
    public final void run() {
        // UI 준비
        mainHandler.post(this::onPreExecute);
        
        try {
            // 백그라운드 작업 수행
            doInBackground();
            
            // 결과 처리 (UI 스레드에서)
            mainHandler.post(this::onPostExecute);
        } catch (Exception e) {
            Log.e(TAG, "Error in searcher", e);
            mainHandler.post(this::onCancelled);
        }
    }

    protected abstract void doInBackground();

    protected void onPostExecute() {
        if (isCancelled()) {
            return;
        }

        MainActivity activity = activityWeakReference.get();
        if (activity == null)
            return;

        hideActivityLoader(activity);

        if (this.processedPojos.isEmpty()) {
            activity.adapter.clear();
        } else {
            PriorityQueue<Pojo> queue = this.processedPojos;
            int maxResults = getMaxResultCount();
            while (queue.size() > maxResults)
                queue.poll();
            List<Result<?>> results = new ArrayList<>(queue.size());
            while (queue.peek() != null) {
                results.add(Result.fromPojo(activity, queue.poll()));
            }

            activity.beforeListChange();

            activity.adapter.updateResults(activity, results, isRefresh, query);

            activity.afterListChange();
        }

        activity.resetTask();

        long time = System.currentTimeMillis() - start;
        Log.v(TAG, "Time to run query `" + query + "` on " + getClass().getSimpleName() + " to completion: " + time + "ms");
        try {
            JSONObject eventProperties = new JSONObject();
            eventProperties.put("type", getClass().getSimpleName());
            eventProperties.put("length", query.replace("<null>", "").length());
            eventProperties.put("time", time);
            eventProperties.put("allProvidersHaveLoaded", KissApplication.getApplication(activity).getDataHandler().allProvidersHaveLoaded);
            Amplitude.getInstance().logEvent("Search", eventProperties);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    protected void onCancelled() {
        MainActivity activity = activityWeakReference.get();
        if (activity == null)
            return;

        hideActivityLoader(activity);
    }

    public Future<?> executeOnExecutor(ExecutorService executor) {
        this.task = executor.submit(this);
        return this.task;
    }

    public boolean isCancelled() {
        return cancelled || (task != null && task.isCancelled());
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        cancelled = true;
        if (task != null) {
            return task.cancel(mayInterruptIfRunning);
        }
        return true;
    }
}
