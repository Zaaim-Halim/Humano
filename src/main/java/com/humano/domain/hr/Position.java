package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.HashSet;
import java.util.Objects;
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
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Position name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public Position description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLevel() {
        return level;
    }

    public Position level(String level) {
        this.level = level;
        return this;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public Set<Employee> getEmployees() {
        return employees;
    }

    public Position employees(Set<Employee> employees) {
        this.employees = employees;
        return this;
    }

    public void setEmployees(Set<Employee> employees) {
        this.employees = employees;
    }

    public Position addEmployee(Employee employee) {
        this.employees.add(employee);
        employee.setPosition(this);
        return this;
    }

    public Position removeEmployee(Employee employee) {
        this.employees.remove(employee);
        employee.setPosition(null);
        return this;
    }

    public OrganizationalUnit getUnit() {
        return unit;
    }

    public Position unit(OrganizationalUnit unit) {
        this.unit = unit;
        return this;
    }

    public void setUnit(OrganizationalUnit unit) {
        this.unit = unit;
    }

    public Position getParentPosition() {
        return parentPosition;
    }

    public Position parentPosition(Position parentPosition) {
        this.parentPosition = parentPosition;
        return this;
    }

    public void setParentPosition(Position parentPosition) {
        this.parentPosition = parentPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Position position = (Position) o;
        return Objects.equals(id, position.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Position{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", level='" + level + '\'' +
            ", employeeCount=" + (employees != null ? employees.size() : 0) +
            '}';
    }
}
