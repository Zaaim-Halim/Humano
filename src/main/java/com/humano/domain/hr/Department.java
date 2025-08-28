package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a department within the organization, grouping employees by function or business unit.
 * <p>
 * Contains department name, description, and associated employees.
 */
@Entity
@Table(name = "department")
public class Department extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Name of the department.
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Optional description of the department.
     */
    @Column(name = "description")
    private String description;

    /**
     * Employees assigned to this department.
     */
    @OneToMany(mappedBy = "department")
    private Set<Employee> employees = new HashSet<>();

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
