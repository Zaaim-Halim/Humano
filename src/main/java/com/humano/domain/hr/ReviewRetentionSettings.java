package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * How long review records are kept — a <b>singleton per tenant schema</b>, created on the first save.
 * A cycle is due for deletion once it is archived and its review period ended more than
 * {@code retainYears} ago; with {@code enabled}, a daily job deletes due cycles, otherwise HR does it
 * from the retention page. Saving never deletes anything by itself.
 */
@Entity
@Table(name = "review_retention_settings")
public class ReviewRetentionSettings extends AbstractAuditingEntity<UUID> {

    public static final int DEFAULT_RETAIN_YEARS = 6;
    public static final int MIN_RETAIN_YEARS = 1;
    public static final int MAX_RETAIN_YEARS = 30;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Whether the daily job deletes due cycles; off until HR turns it on. */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /** Whole years after the review period ends. */
    @Column(name = "retain_years", nullable = false)
    private int retainYears = DEFAULT_RETAIN_YEARS;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetainYears() {
        return retainYears;
    }

    public void setRetainYears(int retainYears) {
        this.retainYears = retainYears;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewRetentionSettings that = (ReviewRetentionSettings) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
