package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/**
 * Represents a custom attribute for an employee, stored as a key-value pair.
 * <p>
 * Used to extend employee records with additional, flexible information.
 */
@Entity
@Table(name = "employee_attribute")
public class EmployeeAttribute extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Key for the attribute (e.g., "language", "certification").
     */
    @Column(name = "attribute_key", nullable = false)
    private String key;

    /**
     * Value for the attribute.
     */
    @Column(name = "attribute_value")
    private String value;

    /**
     * The employee this attribute belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Override
    public UUID getId() {
        return null;
    }

    // Getters and setters

}
