package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Country;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents an employee in the organization, including job details, department, position, and related records.
 * <p>
 * Central entity for HR management, linking to documents, attributes, attendance, leave, and more.
 */
@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends AbstractAuditingEntity<UUID> {

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
     * Job title of the employee.
     */
    @Column(name = "job_title")
    private String jobTitle;
    /**
     * Phone number of the employee.
     */
    @Column(name = "phone")
    private String phone;
    /**
     * Date the employee started.
     */
    @Column(name = "start_date")
    private LocalDate startDate;
    /**
     * Date the employee ended (if applicable).
     */
    @Column(name = "end_date")
    private LocalDate endDate;
    /**
     * Current status of the employee (e.g., ACTIVE, INACTIVE).
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private EmployeeStatus status;
    /**
     * Country of employment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Country country;
    /**
     * Department the employee belongs to.
     */
    @ManyToOne
    @JoinColumn(name = "department_id")
    private Department department;
    /**
     * Position held by the employee.
     */
    @ManyToOne
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;
    /**
     * Documents associated with the employee.
     */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeDocument> documents = new HashSet<>();
    /**
     * Custom attributes for the employee.
     */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeAttribute> attributes = new HashSet<>();
    /**
     * Attendance records for the employee.
     */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Attendance> attendances = new HashSet<>();
    /**
     * Leave requests made by the employee.
     */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<LeaveRequest> leaveRequests = new HashSet<>();

    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Timesheet> timesheets = new HashSet<>();

    // Link to unit
    @ManyToOne
    @JoinColumn(name = "unit_id", nullable = false)
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
    @Column(name = "path", nullable = false, length = 1000)
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
