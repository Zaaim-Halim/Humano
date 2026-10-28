package com.humano.domain.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The retention period for workspaces whose subscription ended, as set in the platform console, in force
 * from {@link #effectiveFrom}. Kept as a history rather than one value, because what a customer was
 * promised depends on when: a longer period applies to everyone at once, a shorter one only to
 * subscriptions that end after it takes effect (see {@code WorkspaceRetentionPolicy}). Before the first
 * version, the configured default ({@code humano.retention.workspace-deletion.*}) applies.
 */
@Entity
@Table(name = "retention_period_version")
public class RetentionPeriodVersion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    /** Days a workspace is kept after its subscription ended. */
    @Column(name = "days", nullable = false)
    private int days;

    /** Days before deletion the admin is warned (and the least notice after the warning). */
    @Column(name = "warning_days", nullable = false)
    private int warningDays;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 254)
    private String createdBy;

    public UUID getId() {
        return id;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(Instant effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getWarningDays() {
        return warningDays;
    }

    public void setWarningDays(int warningDays) {
        this.warningDays = warningDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
