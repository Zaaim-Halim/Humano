package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;

/**
 * Represents a type of benefit provided to employees, such as health insurance, transport allowance, or other perks.
 * <p>
 * This entity models the core attributes of a benefit, including its name, monetary amount, and description.
 * Benefits are associated with employees via a many-to-many relationship, allowing flexible assignment of multiple benefits to multiple employees.
 * <p>
 * Part of the HR domain model, this class helps manage and track employee compensation and perks.
 */
@Entity
@Table(name = "benefit")
public class Benefit extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Name of the benefit (e.g., Health Insurance, Transport Allowance).
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Monetary value or amount associated with the benefit.
     */
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    /**
     * Optional description providing additional details about the benefit.
     */
    @Column(name = "description")
    private String description;

    /**
     * Employees who are assigned this benefit.
     * Many-to-many relationship: an employee can have multiple benefits, and a benefit can be assigned to multiple employees.
     */
    @ManyToMany
    @JoinTable(
        name = "employee_benefit",
        joinColumns = @JoinColumn(name = "benefit_id"),
        inverseJoinColumns = @JoinColumn(name = "employee_id")
    )
    private java.util.Set<Employee> employees = new HashSet<>();

    // Getters and setters

    @Override
    public UUID getId() {
        return id;
    }
}
