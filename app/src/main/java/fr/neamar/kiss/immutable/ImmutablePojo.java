package fr.neamar.kiss.pojo.immutable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import fr.neamar.kiss.normalizer.StringNormalizer;

/**
 * 불변 Pojo 기본 클래스
 * 모든 필드가 final이며 변경 불가능한 구조를 제공합니다.
 */
public abstract class ImmutablePojo {
    public static final String DEFAULT_ID = "(none)";

    @NonNull
    public final String id;
    
    @Nullable
    public final StringNormalizer.Result normalizedName;
    
    public final int relevance;
    
    @NonNull
    public final String name;

    protected ImmutablePojo(@NonNull String id, @NonNull String name, int relevance) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null");
        this.relevance = relevance;
        this.normalizedName = StringNormalizer.normalizeWithResult(this.name, false);
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public int getRelevance() {
        return relevance;
    }

    @Nullable
    public StringNormalizer.Result getNormalizedName() {
        return normalizedName;
    }

    /**
     * Builder 패턴을 위한 추상 클래스
     */
    public abstract static class Builder<T extends ImmutablePojo, B extends Builder<T, B>> {
        protected String id = DEFAULT_ID;
        protected String name = "";
        protected int relevance = 0;

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public B setId(@NonNull String id) {
            this.id = Objects.requireNonNull(id);
            return self();
        }

        public B setName(@NonNull String name) {
            this.name = Objects.requireNonNull(name);
            return self();
        }

        public B setRelevance(int relevance) {
            this.relevance = relevance;
            return self();
        }

        public abstract T build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImmutablePojo)) return false;
        ImmutablePojo that = (ImmutablePojo) o;
        return relevance == that.relevance &&
                id.equals(that.id) &&
                name.equals(that.name) &&
                Objects.equals(normalizedName, that.normalizedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, normalizedName, relevance, name);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", relevance=" + relevance +
                '}';
    }
}
