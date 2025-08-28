package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a job position within the organization, including name, description, level, and relationships to employees and units.
 * <p>
 * Used to define the structure of roles and reporting lines in the company.
 */
@Entity
@Table(name = "position")
public class Position extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Name of the position (e.g., Software Engineer, Manager).
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Optional description of the position.
     */
    @Column(name = "description")
    private String description;

    /**
     * Level of the position (e.g., Junior, Senior, Lead).
     */
    @Column(name = "level", nullable = false)
    private String level;

    /**
     * Employees assigned to this position.
     */
    @OneToMany(mappedBy = "position")
    private Set<Employee> employees = new HashSet<>();

    /**
     * Organizational unit this position belongs to.
     */
    @ManyToOne
    @JoinColumn(name = "unit_id")
    private OrganizationalUnit unit;

    /**
     * Parent position in the hierarchy, if applicable.
     */
    @ManyToOne
    @JoinColumn(name = "parent_position_id")
    private Position parentPosition; // For hierarchy if needed

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Employee> getEmployees() {
        return employees;
    }

    public void setEmployees(Set<Employee> employees) {
        this.employees = employees;
    }
}
