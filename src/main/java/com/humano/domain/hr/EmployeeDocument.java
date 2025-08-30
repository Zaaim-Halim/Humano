package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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

    public UUID getId() {
        return id;
    }

}
