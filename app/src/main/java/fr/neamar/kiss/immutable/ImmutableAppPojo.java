package fr.neamar.kiss.pojo.immutable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import fr.neamar.kiss.utils.UserHandle;

/**
 * 불변 App Pojo 클래스
 * 앱 정보를 담는 불변 객체입니다.
 */
public final class ImmutableAppPojo extends ImmutablePojo {
    
    @NonNull
    public final String packageName;
    
    @NonNull
    public final String activityName;
    
    @NonNull
    public final UserHandle userHandle;
    
    public final boolean excluded;
    public final boolean excludedFromHistory;
    public final boolean excludedShortcuts;
    public final boolean disabled;
    public final long customIconId;
    
    @NonNull
    public final Set<String> tags;

    private ImmutableAppPojo(Builder builder) {
        super(builder.id, builder.name, builder.relevance);
        this.packageName = Objects.requireNonNull(builder.packageName);
        this.activityName = Objects.requireNonNull(builder.activityName);
        this.userHandle = Objects.requireNonNull(builder.userHandle);
        this.excluded = builder.excluded;
        this.excludedFromHistory = builder.excludedFromHistory;
        this.excludedShortcuts = builder.excludedShortcuts;
        this.disabled = builder.disabled;
        this.customIconId = builder.customIconId;
        this.tags = Collections.unmodifiableSet(builder.tags);
    }

    @NonNull
    public String getComponentName() {
        return getComponentName(packageName, activityName, userHandle);
    }

    @NonNull
    public static String getComponentName(@NonNull String packageName, 
                                        @NonNull String activityName,
                                        @NonNull UserHandle userHandle) {
        return userHandle.addUserSuffixToString(packageName + "/" + activityName, '#');
    }

    /**
     * 새로운 인스턴스를 생성하되 특정 필드만 변경합니다.
     */
    public ImmutableAppPojo withExcluded(boolean excluded) {
        if (this.excluded == excluded) {
            return this;
        }
        return new Builder(this).setExcluded(excluded).build();
    }

    public ImmutableAppPojo withExcludedFromHistory(boolean excludedFromHistory) {
        if (this.excludedFromHistory == excludedFromHistory) {
            return this;
        }
        return new Builder(this).setExcludedFromHistory(excludedFromHistory).build();
    }

    public ImmutableAppPojo withExcludedShortcuts(boolean excludedShortcuts) {
        if (this.excludedShortcuts == excludedShortcuts) {
            return this;
        }
        return new Builder(this).setExcludedShortcuts(excludedShortcuts).build();
    }

    public ImmutableAppPojo withCustomIconId(long customIconId) {
        if (this.customIconId == customIconId) {
            return this;
        }
        return new Builder(this).setCustomIconId(customIconId).build();
    }

    public ImmutableAppPojo withTags(@NonNull Set<String> tags) {
        if (this.tags.equals(tags)) {
            return this;
        }
        return new Builder(this).setTags(tags).build();
    }

    public static class Builder extends ImmutablePojo.Builder<ImmutableAppPojo, Builder> {
        private String packageName = "";
        private String activityName = "";
        private UserHandle userHandle = new UserHandle();
        private boolean excluded = false;
        private boolean excludedFromHistory = false;
        private boolean excludedShortcuts = false;
        private boolean disabled = false;
        private long customIconId = 0;
        private Set<String> tags = Collections.emptySet();

        public Builder() {
            // 기본 생성자
        }

        public Builder(@NonNull ImmutableAppPojo source) {
            super.setId(source.id)
                 .setName(source.name)
                 .setRelevance(source.relevance);
            this.packageName = source.packageName;
            this.activityName = source.activityName;
            this.userHandle = source.userHandle;
            this.excluded = source.excluded;
            this.excludedFromHistory = source.excludedFromHistory;
            this.excludedShortcuts = source.excludedShortcuts;
            this.disabled = source.disabled;
            this.customIconId = source.customIconId;
            this.tags = source.tags;
        }

        public Builder setPackageName(@NonNull String packageName) {
            this.packageName = Objects.requireNonNull(packageName);
            return this;
        }

        public Builder setActivityName(@NonNull String activityName) {
            this.activityName = Objects.requireNonNull(activityName);
            return this;
        }

        public Builder setUserHandle(@NonNull UserHandle userHandle) {
            this.userHandle = Objects.requireNonNull(userHandle);
            return this;
        }

        public Builder setExcluded(boolean excluded) {
            this.excluded = excluded;
            return this;
        }

        public Builder setExcludedFromHistory(boolean excludedFromHistory) {
            this.excludedFromHistory = excludedFromHistory;
            return this;
        }

        public Builder setExcludedShortcuts(boolean excludedShortcuts) {
            this.excludedShortcuts = excludedShortcuts;
            return this;
        }

        public Builder setDisabled(boolean disabled) {
            this.disabled = disabled;
            return this;
        }

        public Builder setCustomIconId(long customIconId) {
            this.customIconId = customIconId;
            return this;
        }

        public Builder setTags(@NonNull Set<String> tags) {
            this.tags = Objects.requireNonNull(tags);
            return this;
        }

        @Override
        public ImmutableAppPojo build() {
            return new ImmutableAppPojo(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutableAppPojo)) return false;
        if (!super.equals(o)) return false;
        ImmutableAppPojo that = (ImmutableAppPojo) o;
        return excluded == that.excluded &&
                excludedFromHistory == that.excludedFromHistory &&
                excludedShortcuts == that.excludedShortcuts &&
                disabled == that.disabled &&
                customIconId == that.customIconId &&
                packageName.equals(that.packageName) &&
                activityName.equals(that.activityName) &&
                userHandle.equals(that.userHandle) &&
                tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), packageName, activityName, userHandle, 
                           excluded, excludedFromHistory, excludedShortcuts, disabled, 
                           customIconId, tags);
    }
}
