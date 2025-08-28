package android.app;

/**
 * @hide
 */
interface IActivityManager {
    void forceStopPackage(String packageName, int userId, int flags);
}