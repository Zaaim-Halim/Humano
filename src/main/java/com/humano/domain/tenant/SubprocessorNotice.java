package com.humano.domain.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A notice sent to every customer that a new sub-processor will start processing their data, as the
 * Data Processing Addendum requires ahead of time. Kept as the record that the notice went out, to whom
 * (how many) and how far ahead of {@link #effectiveFrom}.
 */
@Entity
@Table(name = "subprocessor_notice")
public class SubprocessorNotice {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Column(name = "data_categories", nullable = false, length = 500)
    private String dataCategories;

    @Column(name = "location", nullable = false, length = 200)
    private String location;

    /** The day the sub-processor starts processing customer data. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "sent_by", length = 254)
    private String sentBy;

    /** Workspaces whose contact the notice was sent to. */
    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    /** Workspaces with no contact address, which did not get it. */
    @Column(name = "missing_count", nullable = false)
    private int missingCount;

    /** Workspaces it could not be sent to (mail or workspace error); see the log. */
    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getDataCategories() {
        return dataCategories;
    }

    public void setDataCategories(String dataCategories) {
        this.dataCategories = dataCategories;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getSentBy() {
        return sentBy;
    }

    public void setSentBy(String sentBy) {
        this.sentBy = sentBy;
    }

    public int getRecipientCount() {
        return recipientCount;
    }

    public void setRecipientCount(int recipientCount) {
        this.recipientCount = recipientCount;
    }

    public int getMissingCount() {
        return missingCount;
    }

    public void setMissingCount(int missingCount) {
        this.missingCount = missingCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
}
