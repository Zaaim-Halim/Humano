package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Country;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
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

    /**
     * Skills possessed by the employee.
     */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<EmployeeSkill> skills = new HashSet<>();

    @PrePersist
    @PreUpdate
    public void updatePath() {
        this.path = (manager != null ? manager.getPath() : "") + "/" + getId().toString();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public Employee jobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
        return this;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getPhone() {
        return phone;
    }

    public Employee phone(String phone) {
        this.phone = phone;
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public Employee startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Employee endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public EmployeeStatus getStatus() {
        return status;
    }

    public Employee status(EmployeeStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(EmployeeStatus status) {
        this.status = status;
    }

    public Country getCountry() {
        return country;
    }

    public Employee country(Country country) {
        this.country = country;
        return this;
    }

    public void setCountry(Country country) {
        this.country = country;
    }

    public Department getDepartment() {
        return department;
    }

    public Employee department(Department department) {
        this.department = department;
        return this;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Position getPosition() {
        return position;
    }

    public Employee position(Position position) {
        this.position = position;
        return this;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public Set<EmployeeDocument> getDocuments() {
        return documents;
    }

    public Employee documents(Set<EmployeeDocument> documents) {
        this.documents = documents;
        return this;
    }

    public void setDocuments(Set<EmployeeDocument> documents) {
        this.documents = documents;
    }

    public Employee addDocument(EmployeeDocument document) {
        this.documents.add(document);
        document.setEmployee(this);
        return this;
    }

    public Employee removeDocument(EmployeeDocument document) {
        this.documents.remove(document);
        return this;
    }

    public Set<EmployeeAttribute> getAttributes() {
        return attributes;
    }

    public Employee attributes(Set<EmployeeAttribute> attributes) {
        this.attributes = attributes;
        return this;
    }

    public void setAttributes(Set<EmployeeAttribute> attributes) {
        this.attributes = attributes;
    }

    public Employee addAttribute(EmployeeAttribute attribute) {
        this.attributes.add(attribute);
        attribute.setEmployee(this);
        return this;
    }

    public Employee removeAttribute(EmployeeAttribute attribute) {
        this.attributes.remove(attribute);
        return this;
    }

    public Set<Attendance> getAttendances() {
        return attendances;
    }

    public Employee attendances(Set<Attendance> attendances) {
        this.attendances = attendances;
        return this;
    }

    public void setAttendances(Set<Attendance> attendances) {
        this.attendances = attendances;
    }

    public Employee addAttendance(Attendance attendance) {
        this.attendances.add(attendance);
        attendance.setEmployee(this);
        return this;
    }

    public Employee removeAttendance(Attendance attendance) {
        this.attendances.remove(attendance);
        return this;
    }

    public Set<LeaveRequest> getLeaveRequests() {
        return leaveRequests;
    }

    public Employee leaveRequests(Set<LeaveRequest> leaveRequests) {
        this.leaveRequests = leaveRequests;
        return this;
    }

    public void setLeaveRequests(Set<LeaveRequest> leaveRequests) {
        this.leaveRequests = leaveRequests;
    }

    public Employee addLeaveRequest(LeaveRequest leaveRequest) {
        this.leaveRequests.add(leaveRequest);
        leaveRequest.setEmployee(this);
        return this;
    }

    public Employee removeLeaveRequest(LeaveRequest leaveRequest) {
        this.leaveRequests.remove(leaveRequest);
        return this;
    }

    public Set<Timesheet> getTimesheets() {
        return timesheets;
    }

    public Employee timesheets(Set<Timesheet> timesheets) {
        this.timesheets = timesheets;
        return this;
    }

    public void setTimesheets(Set<Timesheet> timesheets) {
        this.timesheets = timesheets;
    }

    public Employee addTimesheet(Timesheet timesheet) {
        this.timesheets.add(timesheet);
        timesheet.setEmployee(this);
        return this;
    }

    public Employee removeTimesheet(Timesheet timesheet) {
        this.timesheets.remove(timesheet);
        return this;
    }

    public OrganizationalUnit getUnit() {
        return unit;
    }

    public Employee unit(OrganizationalUnit unit) {
        this.unit = unit;
        return this;
    }

    public void setUnit(OrganizationalUnit unit) {
        this.unit = unit;
    }

    public Employee getManager() {
        return manager;
    }

    public Employee manager(Employee manager) {
        this.manager = manager;
        return this;
    }

    public void setManager(Employee manager) {
        this.manager = manager;
    }

    public Set<Employee> getSubordinates() {
        return subordinates;
    }

    public Employee subordinates(Set<Employee> subordinates) {
        this.subordinates = subordinates;
        return this;
    }

    public void setSubordinates(Set<Employee> subordinates) {
        this.subordinates = subordinates;
    }

    public Employee addSubordinate(Employee subordinate) {
        this.subordinates.add(subordinate);
        subordinate.setManager(this);
        return this;
    }

    public Employee removeSubordinate(Employee subordinate) {
        this.subordinates.remove(subordinate);
        return this;
    }

    public String getPath() {
        return path;
    }

    public Employee path(String path) {
        this.path = path;
        return this;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Set<EmployeeSkill> getSkills() {
        return skills;
    }

    public Employee skills(Set<EmployeeSkill> skills) {
        this.skills = skills;
        return this;
    }

    public void setSkills(Set<EmployeeSkill> skills) {
        this.skills = skills;
    }

    public EmployeeSkill addSkill(Skill skill, Integer proficiencyLevel) {
        EmployeeSkill employeeSkill = new EmployeeSkill();
        employeeSkill.setEmployee(this);
        employeeSkill.setSkill(skill);
        employeeSkill.setProficiencyLevel(proficiencyLevel);
        employeeSkill.setAcquisitionDate(LocalDate.now());
        employeeSkill.setVerified(false);
        this.skills.add(employeeSkill);
        return employeeSkill;
    }

    public Employee removeSkill(EmployeeSkill employeeSkill) {
        this.skills.remove(employeeSkill);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Employee employee = (Employee) o;
        return Objects.equals(id, employee.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Employee{" +
            "id=" + id +
            ", jobTitle='" + jobTitle + '\'' +
            ", phone='" + phone + '\'' +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            ", status=" + status +
            ", path='" + path + '\'' +
            '}';
    }
}
