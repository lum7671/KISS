package fr.neamar.kiss.pojo;

import android.os.Build;

import fr.neamar.kiss.utils.UserHandle;

public final class AppPojo extends PojoWithTags {

    public static String getComponentName(String packageName, String activityName,
                                          UserHandle userHandle) {
        return userHandle.addUserSuffixToString(packageName + "/" + activityName, '#');
    }

    public final String packageName;
    public final String activityName;
    public final UserHandle userHandle;

    private boolean excluded;
    private boolean excludedFromHistory;
    /**
     * Whether shortcuts are excluded for this app
     */
    private boolean excludedShortcuts;
    private long customIconId = 0;
    private final boolean disabled;

    public AppPojo(String id, String packageName, String activityName, UserHandle userHandle,
                   boolean isExcluded, boolean isExcludedFromHistory, boolean isExcludedShortcuts, boolean disabled) {
        super(id);

        this.packageName = packageName;
        this.activityName = activityName;
        this.userHandle = userHandle;

        this.excluded = isExcluded;
        this.excludedFromHistory = isExcludedFromHistory;
        this.excludedShortcuts = isExcludedShortcuts;
        this.disabled = disabled;
    }

    public String getComponentName() {
        return getComponentName(packageName, activityName, userHandle);
    }

    public boolean isExcluded() {
        return excluded;
    }

    public void setExcluded(boolean excluded) {
        this.excluded = excluded;
    }

    public boolean isExcludedFromHistory() {
        return excludedFromHistory;
    }

    public void setExcludedFromHistory(boolean excludedFromHistory) {
        this.excludedFromHistory = excludedFromHistory;
    }

    public boolean isExcludedShortcuts() {
        return excludedShortcuts;
    }

    public void setExcludedShortcuts(boolean excludedShortcuts) {
        this.excludedShortcuts = excludedShortcuts;
    }

    public void setCustomIconId(long iconId) {
        customIconId = iconId;
    }

    public long getCustomIconId() {
        return customIconId;
    }

    public String getPackageKey() {
        return userHandle.getRealHandle().hashCode() + "|" + packageName;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }
}
