package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a document associated with an employee, such as contracts, certificates, or identification.
 * <p>
 * Stores document type, file path, and the related employee.
 */
@Entity
@Table(name = "employee_document")
public class EmployeeDocument extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
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
        return "EmployeeDocument{" +
            "id=" + id +
            ", type='" + type + '\'' +
            ", filePath='" + filePath + '\'' +
            '}';
    }
}
