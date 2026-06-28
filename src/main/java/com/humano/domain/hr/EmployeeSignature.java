package com.humano.domain.hr;

import com.humano.domain.shared.AbstractEmployeeOwnedEntity;
import jakarta.persistence.*;
import java.util.UUID;

/**
 * An employee's digital signature, used for signing documents. One per employee (enforced by a
 * unique constraint on {@code employee_id}). The creation timestamp is the inherited audit
 * {@code created_date}. {@link #signatureFileId} is a soft reference to the {@code stored_file}
 * holding the signature image.
 */
@Entity
@Table(name = "employee_signature")
public class EmployeeSignature extends AbstractEmployeeOwnedEntity {

    /** Soft reference to the backing {@code stored_file.id} holding the signature image. */
    @Column(name = "signature_file_id")
    private UUID signatureFileId;

    /** Optional signing certificate reference / payload. */
    @Column(name = "certificate", length = 4000)
    private String certificate;

    public UUID getSignatureFileId() {
        return signatureFileId;
    }

    public EmployeeSignature signatureFileId(UUID signatureFileId) {
        this.signatureFileId = signatureFileId;
        return this;
    }

    public void setSignatureFileId(UUID signatureFileId) {
        this.signatureFileId = signatureFileId;
    }

    public String getCertificate() {
        return certificate;
    }

    public EmployeeSignature certificate(String certificate) {
        this.certificate = certificate;
        return this;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    @Override
    public String toString() {
        return "EmployeeSignature{id=" + getId() + '}';
    }
}
