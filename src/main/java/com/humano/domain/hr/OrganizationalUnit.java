package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "organizational_unit")
public class OrganizationalUnit extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name; // e.g., "Finance", "Payroll"

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrganizationalUnitType type; // DEPARTMENT, DIRECTORATE, SECTOR

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_unit_id")
    private OrganizationalUnit parentUnit;

    @OneToMany(mappedBy = "parentUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrganizationalUnit> subUnits = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Employee> employees = new HashSet<>();
    /*
     * The path is a precomputed field that represents the full path of the organizational unit
     * in the hierarchy, e.g., "/Finance/Accounting/Payroll". It is computed based on the parent unit.
     * @Query("SELECT u FROM OrganizationalUnit u WHERE u.path LIKE CONCAT(:path, '/%')")
       List<OrganizationalUnit> findAllSubUnits(@Param("path") String path);
     */

    @Column(name = "path", nullable = false)
    private String path; // e.g., "/Finance/Accounting/Payroll"

    // Precompute path when persisting or updating
    @PrePersist
    @PreUpdate
    public void updatePath() {
        this.path = (parentUnit != null ? parentUnit.getPath() : "") + "/" + name;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
