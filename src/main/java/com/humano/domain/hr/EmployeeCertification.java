package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Professional certification held by an employee. {@link #documentFileId} is a soft reference to a
 * {@code stored_file} row holding the scanned certificate.
 */
@Entity
@Table(name = "employee_certification")
public class EmployeeCertification extends AbstractEmployeeOwnedEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "issuer")
    private String issuer;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "verified", nullable = false)
    private Boolean verified = false;

    /** Soft reference to the backing {@code stored_file.id}. */
    @Column(name = "document_file_id")
    private UUID documentFileId;

    public String getName() {
        return name;
    }

    public EmployeeCertification name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuer() {
        return issuer;
    }

    public EmployeeCertification issuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public EmployeeCertification issueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
        return this;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public EmployeeCertification expiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Boolean getVerified() {
        return verified;
    }

    public EmployeeCertification verified(Boolean verified) {
        this.verified = verified;
        return this;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public UUID getDocumentFileId() {
        return documentFileId;
    }

    public EmployeeCertification documentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
        return this;
    }

    public void setDocumentFileId(UUID documentFileId) {
        this.documentFileId = documentFileId;
    }

    @Override
    public String toString() {
        return "EmployeeCertification{id=" + getId() + ", name='" + name + "', verified=" + verified + '}';
    }
}
