package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
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
    @Column(name = "description", length = 500)
    private String description;

    /**
     * Employees assigned to this department.
     */
    @OneToMany(mappedBy = "department")
    private Set<Employee> employees = new HashSet<>();

    /**
     * The head/manager of this department.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "head_id")
    private Employee head;

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

    public Department name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public Department description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Employee> getEmployees() {
        return employees;
    }

    public Department employees(Set<Employee> employees) {
        this.employees = employees;
        return this;
    }

    public void setEmployees(Set<Employee> employees) {
        this.employees = employees;
    }

    public Employee getHead() {
        return head;
    }

    public Department head(Employee head) {
        this.head = head;
        return this;
    }

    public void setHead(Employee head) {
        this.head = head;
    }

    public Department addEmployee(Employee employee) {
        this.employees.add(employee);
        employee.setDepartment(this);
        return this;
    }

    public Department removeEmployee(Employee employee) {
        this.employees.remove(employee);
        employee.setDepartment(null);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Department that = (Department) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Department{" + "id=" + id + ", name='" + name + '\'' + ", description='" + description + '\'' + '}';
    }
}
