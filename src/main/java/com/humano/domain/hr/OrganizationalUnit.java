package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an organizational unit in the company hierarchy, such as a department, sector, or directorate.
 * <p>
 * Supports parent/child relationships, manager assignment, and hierarchical path computation.
 */
@Entity
@Table(name = "organizational_unit")
public class OrganizationalUnit extends AbstractAuditingEntity<UUID> {
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
     * Name of the organizational unit (e.g., Finance, Payroll).
     */
    @Column(name = "name", nullable = false)
    private String name; // e.g., "Finance", "Payroll"

    /**
     * Type of the unit (e.g., DEPARTMENT, SECTOR).
     */
    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrganizationalUnitType type; // DEPARTMENT, DIRECTORATE, SECTOR

    /**
     * Parent unit in the hierarchy.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_unit_id")
    private OrganizationalUnit parentUnit;

    /**
     * Sub-units under this unit.
     */
    @OneToMany(mappedBy = "parentUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrganizationalUnit> subUnits = new HashSet<>();

    /**
     * Manager assigned to this unit.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    /**
     * Employees assigned to this unit.
     */
    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Employee> employees = new HashSet<>();
    /*
     * The path is a precomputed field that represents the full path of the organizational unit
     * in the hierarchy, e.g., "/Finance/Accounting/Payroll". It is computed based on the parent unit.
     * @Query("SELECT u FROM OrganizationalUnit u WHERE u.path LIKE CONCAT(:path, '/%')")
       List<OrganizationalUnit> findAllSubUnits(@Param("path") String path);
     */

    /**
     * Hierarchical path of the unit (e.g., /Finance/Accounting/Payroll).
     */
    @Column(name = "path", nullable = false, length = 1000)
    private String path; // e.g., "/Finance/Accounting/Payroll"

    // Precompute path when persisting or updating
    @PrePersist
    @PreUpdate
    public void updatePath() {
        this.path = (parentUnit != null ? parentUnit.getPath() : "") + "/" + name;
    }

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

    public OrganizationalUnit name(String name) {
        this.name = name;
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public OrganizationalUnitType getType() {
        return type;
    }

    public OrganizationalUnit type(OrganizationalUnitType type) {
        this.type = type;
        return this;
    }

    public void setType(OrganizationalUnitType type) {
        this.type = type;
    }

    public OrganizationalUnit getParentUnit() {
        return parentUnit;
    }

    public OrganizationalUnit parentUnit(OrganizationalUnit parentUnit) {
        this.parentUnit = parentUnit;
        return this;
    }

    public void setParentUnit(OrganizationalUnit parentUnit) {
        this.parentUnit = parentUnit;
    }

    public Set<OrganizationalUnit> getSubUnits() {
        return subUnits;
    }

    public OrganizationalUnit subUnits(Set<OrganizationalUnit> subUnits) {
        this.subUnits = subUnits;
        return this;
    }

    public void setSubUnits(Set<OrganizationalUnit> subUnits) {
        this.subUnits = subUnits;
    }

    public OrganizationalUnit addSubUnit(OrganizationalUnit subUnit) {
        this.subUnits.add(subUnit);
        subUnit.setParentUnit(this);
        return this;
    }

    public OrganizationalUnit removeSubUnit(OrganizationalUnit subUnit) {
        this.subUnits.remove(subUnit);
        subUnit.setParentUnit(null);
        return this;
    }

    public Employee getManager() {
        return manager;
    }

    public OrganizationalUnit manager(Employee manager) {
        this.manager = manager;
        return this;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }

    public Set<Employee> getEmployees() {
        return employees;
    }

    public OrganizationalUnit employees(Set<Employee> employees) {
        this.employees = employees;
        return this;
    }

    public void setEmployees(Set<Employee> employees) {
        this.employees = employees;
    }

    public OrganizationalUnit addEmployee(Employee employee) {
        this.employees.add(employee);
        employee.setUnit(this);
        return this;
    }

    public OrganizationalUnit removeEmployee(Employee employee) {
        this.employees.remove(employee);
        employee.setUnit(null);
        return this;
    }

    public String getPath() {
        return path;
    }

    public OrganizationalUnit path(String path) {
        this.path = path;
        return this;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrganizationalUnit that = (OrganizationalUnit) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "OrganizationalUnit{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", type=" + type +
            ", path='" + path + '\'' +
            '}';
    }
}
