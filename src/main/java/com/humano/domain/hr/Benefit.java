package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
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
    @Min(0)
    private BigDecimal amount;

    /**
     * Optional description providing additional details about the benefit.
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Employees who are assigned this benefit.
     * Many-to-many relationship: an employee can have multiple benefits, and a benefit can be assigned to multiple employees.
     */
    @ManyToMany
    @JoinTable(
        name = "hr_employee_benefit",
        joinColumns = @JoinColumn(name = "benefit_id"),
        inverseJoinColumns = @JoinColumn(name = "employee_id")
    )
    private Set<Employee> employees = new HashSet<>();

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

    public Benefit name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Benefit amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public Benefit description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Employee> getEmployees() {
        return employees;
    }

    public Benefit employees(Set<Employee> employees) {
        this.employees = employees;
        return this;
    }

    public void setEmployees(Set<Employee> employees) {
        this.employees = employees;
    }

    public Benefit addEmployee(Employee employee) {
        this.employees.add(employee);
        return this;
    }

    public Benefit removeEmployee(Employee employee) {
        this.employees.remove(employee);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Benefit benefit = (Benefit) o;
        return Objects.equals(id, benefit.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Benefit{" + "id=" + id + ", name='" + name + '\'' + ", amount=" + amount + ", description='" + description + '\'' + '}';
    }
}
