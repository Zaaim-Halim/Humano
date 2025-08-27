package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_title")
    private String jobTitle;
    @Column(name = "phone")
    private String phone;
    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private per.hzaaim.empmanagement.core.domain.Country country;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne
    @JoinColumn(name = "position_id")
    private Position position;

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeDocument> documents = new HashSet<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeAttribute> attributes = new HashSet<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Attendance> attendances = new HashSet<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LeaveRequest> leaveRequests = new HashSet<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Timesheet> timesheets = new HashSet<>();

    // Link to unit
    @ManyToOne
    @JoinColumn(name = "unit_id")
    private OrganizationalUnit unit;

    // Direct manager (can be different from unit manager)
    @ManyToOne
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @OneToMany(mappedBy = "manager")
    private Set<Employee> subordinates = new HashSet<>();

    /* * Materialized path for employee hierarchy
     * This is a string representation of the hierarchy path, e.g., "/1/2/3" where 1 is the root employee,
     * 2 is a subordinate, and 3 is a further subordinate.
     *
     * @Query("SELECT e FROM Employee e WHERE e.path LIKE CONCAT(:managerPath, '/%')")
       List<Employee> findAllSubordinates(@Param("managerPath") String managerPath);
     */
    @Column(name = "path", nullable = false)
    private String path; // materialized path for employee hierarchy

    @PrePersist
    @PreUpdate
    public void updatePath() {
        this.path = (manager != null ? manager.getPath() : "") + "/" + getId().toString();
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
