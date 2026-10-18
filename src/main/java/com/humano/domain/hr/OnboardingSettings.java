package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Company onboarding policy — a <b>singleton per tenant schema</b>, created on
 * the first save. Holds the default onboarding length: the finish-by date a new
 * onboarding gets when none is chosen, and the baseline the checklist's due
 * days ({@link OnboardingTaskTemplate#getDueDay()}) are written against.
 */
@Entity
@Table(name = "onboarding_settings")
public class OnboardingSettings extends AbstractAuditingEntity<UUID> {

    /** Default onboarding length when nothing has been saved. */
    public static final int DEFAULT_DAYS = 30;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Days from the start date to the default finish-by date (default 30). */
    @Column(name = "default_days", nullable = false)
    private int defaultDays = DEFAULT_DAYS;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public int getDefaultDays() {
        return defaultDays;
    }

    public void setDefaultDays(int defaultDays) {
        this.defaultDays = defaultDays;
    }

    @Override
    public String toString() {
        return "OnboardingSettings{id=" + id + ", defaultDays=" + defaultDays + "}";
    }
}
