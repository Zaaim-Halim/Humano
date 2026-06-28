package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Work permit / visa held by an employee. {@link #documentFileId} is a soft reference to a
 * {@code stored_file} row holding the scanned permit (same file-storage pattern as elsewhere).
 */
@Entity
@Table(name = "work_permit")
public class WorkPermit extends AbstractEmployeeOwnedEntity {

    @Column(name = "visa_type")
    private String visaType;

    @Column(name = "permit_number")
    private String permitNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "sponsor")
    private String sponsor;

    /** Soft reference to the backing {@code stored_file.id}. */
    @Column(name = "document_file_id")
    private UUID documentFileId;

    public String getVisaType() {
        return visaType;
    }

    public WorkPermit visaType(String visaType) {
        this.visaType = visaType;
        return this;
    }

    public void setVisaType(String visaType) {
        this.visaType = visaType;
    }

    public String getPermitNumber() {
        return permitNumber;
    }

    public WorkPermit permitNumber(String permitNumber) {
        this.permitNumber = permitNumber;
        return this;
    }

    public void setPermitNumber(String permitNumber) {
        this.permitNumber = permitNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public WorkPermit issueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
        return this;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public WorkPermit expiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getSponsor() {
        return sponsor;
    }

    public WorkPermit sponsor(String sponsor) {
        this.sponsor = sponsor;
        return this;
    }

    public void setSponsor(String sponsor) {
        this.sponsor = sponsor;
    }

    public UUID getDocumentFileId() {
        return documentFileId;
    }

    public WorkPermit documentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
        return this;
    }

    public void setDocumentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
    }

    @Override
    public String toString() {
        return "WorkPermit{id=" + getId() + ", visaType='" + visaType + "', permitNumber='" + permitNumber + "'}";
    }
}
