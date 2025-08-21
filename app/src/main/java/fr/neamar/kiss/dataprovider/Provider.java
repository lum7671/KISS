package fr.neamar.kiss.dataprovider;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.pojo.Pojo;

public abstract class Provider<T extends Pojo> extends Service implements IProvider<T> {
    private final static String TAG = Provider.class.getSimpleName();

    /**
     * Binder given to clients
     */
    private final IBinder binder = new LocalBinder();
    /**
     * Storage for search items used by this provider
     */
    private List<T> pojos = new ArrayList<>();
    private boolean loaded = false;
    /**
     * Scheme used to build ids for the pojos created by this provider
     */
    private String pojoScheme = "(none)://";

    private long start;
    private kotlinx.coroutines.Job loaderJob;

    /**
     * (Re-)load the providers resources when the provider has been completely initialized
     * by the Android system
     */
    @Override
    public void onCreate() {
        super.onCreate();

        this.reload();
    }

    /**
     * Initialize provider with Kotlin Coroutines-based loader
     * 
     * @param loader The Coroutines-based loader
     */
    void initializeCoroutines(fr.neamar.kiss.loader.LoadPojosCoroutine<T> loader) {
        cancelInitialize();
        start = System.currentTimeMillis();

        Log.i(TAG, "Starting provider (Coroutines): " + this.getClass().getSimpleName());

        loader.setProvider(this);
        this.pojoScheme = loader.getScheme();
        // Store the job for potential cancellation
        this.loaderJob = loader.executeAsync();
    }

    /**
     * Cancel running Coroutines task and set to null.
     */
    private void cancelInitialize() {
        // Cancel Coroutines-based loader
        if (this.loaderJob != null) {
            this.loaderJob.cancel(null);
            this.loaderJob = null;
            Log.i(TAG, "Cancelling provider (Coroutines): " + this.getClass().getSimpleName());
        }
    }

    public void reload() {
        // Handled at subclass level
        if (!pojos.isEmpty()) {
            Log.v(TAG, "Reloading provider: " + this.getClass().getSimpleName());
        }
    }

    public boolean isLoaded() {
        return this.loaded;
    }

    public void loadOver(List<T> results) {
        long time = System.currentTimeMillis() - start;

        Log.i(TAG, "Time to load " + this.getClass().getSimpleName() + ": " + time + "ms");
        // Store results
        this.loaded = true;
        this.pojos = results;

        // Broadcast this event
        Intent i = new Intent(MainActivity.LOAD_OVER);
        this.sendBroadcast(i);
    }

    /**
     * Tells whether or not this provider may be able to find the pojo with
     * specified id
     *
     * @param id id we're looking for
     * @return true if the provider can handle the query ; does not guarantee it
     * will!
     */
    public boolean mayFindById(String id) {
        return id.startsWith(pojoScheme);
    }

    /**
     * Try to find a record by its id
     *
     * @param id id we're looking for
     * @return null if not found
     */
    public T findById(String id) {
        for (T pojo : pojos) {
            if (pojo.id.equals(id)) {
                return pojo;
            }
        }

        return null;
    }

    @Override
    public List<T> getPojos() {
        return Collections.unmodifiableList(pojos);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return this.binder;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public IProvider<T> getService() {
            // Return this instance of the provider so that clients can call public methods
            return Provider.this;
        }
    }
}
