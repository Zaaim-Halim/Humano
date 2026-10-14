package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Represents a document associated with an employee, such as contracts, certificates, or identification.
 * <p>
 * Stores document type, file path, and the related employee.
 */
@Entity
@Table(name = "employee_document")
public class EmployeeDocument extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Type of the document (e.g., contract, certificate).
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * File path to the stored document.
     */
    @Column(name = "file_path", nullable = false)
    private String filePath;

    /** The uploaded file's own name (the stored key is generated). */
    @Column(name = "original_file_name")
    private String originalFileName;

    /** Media type of the uploaded file, e.g. {@code application/pdf}. */
    @Column(name = "content_type", length = 100)
    private String contentType;

    /** Size of the uploaded file in bytes. */
    @Column(name = "size_bytes")
    private Long sizeBytes;

    /**
     * The employee this document belongs to.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getType() {
        return type;
    }

    public EmployeeDocument type(String type) {
        this.type = type;
        return this;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFilePath() {
        return filePath;
    }

    public EmployeeDocument filePath(String filePath) {
        this.filePath = filePath;
        return this;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeDocument employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeDocument that = (EmployeeDocument) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeDocument{" + "id=" + id + ", type='" + type + '\'' + ", filePath='" + filePath + '\'' + '}';
    }
}
