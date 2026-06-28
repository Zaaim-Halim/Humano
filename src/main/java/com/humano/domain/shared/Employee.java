package com.humano.domain.shared;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.Gender;
import com.humano.domain.enumeration.hr.WorkLocationType;
import com.humano.domain.hr.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents an employee in the organization, including job details, department, position, and related records.
 * <p>
 * Central entity for HR management, linking to documents, attributes, attendance, leave, and more.
 * Each Employee is associated with a User account.
 */
@Entity
@Table(name = "employee")
@PrimaryKeyJoinColumn(name = "id")
public class Employee extends User {

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
     * Human-readable employee identifier (e.g. "EMP-000123"). Distinct from the User login.
     */
    @Column(name = "employee_number", unique = true)
    private String employeeNumber;

    /**
     * Date of birth.
     */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * Gender.
     */
    @Column(name = "gender", length = 30)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    /**
     * Nationality of the employee.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nationality_id")
    private Country nationality;

    /**
     * Place of birth.
     */
    @Column(name = "place_of_birth")
    private String placeOfBirth;

    /**
     * Work phone number (the inherited {@link #phone} is the personal number).
     */
    @Column(name = "work_phone")
    private String workPhone;

    /**
     * Work location arrangement (onsite, remote, hybrid).
     */
    @Column(name = "work_location", length = 30)
    @Enumerated(EnumType.STRING)
    private WorkLocationType workLocation;

    /**
     * Full-time-equivalent percentage (e.g. 1.00 = full time, 0.50 = half time).
     */
    @Column(name = "fte", precision = 5, scale = 2)
    private BigDecimal fte;

    /**
     * Date probation ends (null if not on probation).
     */
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    /**
     * Date the employee was confirmed (probation passed).
     */
    @Column(name = "confirmation_date")
    private LocalDate confirmationDate;

    /**
     * Free-text notes about the termination.
     */
    @Column(name = "termination_notes", length = 2000)
    private String terminationNotes;

    /**
     * National identity number. Sensitive PII — encryption-at-rest is deferred (TODO: §6).
     */
    @Column(name = "national_id")
    private String nationalId;

    /**
     * Passport number. Sensitive PII — encryption-at-rest is deferred (TODO: §6).
     */
    @Column(name = "passport_number")
    private String passportNumber;

    /**
     * Tax identification number. Sensitive PII — encryption-at-rest is deferred (TODO: §6).
     */
    @Column(name = "tax_number")
    private String taxNumber;

    /**
     * Social security number (country-dependent). Sensitive PII — encryption-at-rest is deferred (TODO: §6).
     */
    @Column(name = "social_security_number")
    private String socialSecurityNumber;

    /**
     * Marital status (tenant-configurable reference data).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marital_status_id")
    private MaritalStatus maritalStatus;

    /**
     * Employment type (tenant-configurable reference data).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employment_type_id")
    private EmploymentType employmentType;

    /**
     * Job grade / pay band (tenant-configurable reference data).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_id")
    private JobGrade grade;

    /**
     * Job level / seniority (tenant-configurable reference data).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private JobLevel level;

    /**
     * Employment category (tenant-configurable reference data).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private EmployeeCategory category;

    /**
     * Reason for termination (tenant-configurable reference data).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "termination_reason_id")
    private TerminationReason terminationReason;

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
        if (getId() != null) {
            this.path = (manager != null ? manager.getPath() : "") + "/" + getId().toString();
        }
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

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public Employee employeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
        return this;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Employee birthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        return this;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public Employee gender(Gender gender) {
        this.gender = gender;
        return this;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Country getNationality() {
        return nationality;
    }

    public Employee nationality(Country nationality) {
        this.nationality = nationality;
        return this;
    }

    public void setNationality(Country nationality) {
        this.nationality = nationality;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public Employee placeOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
        return this;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getWorkPhone() {
        return workPhone;
    }

    public Employee workPhone(String workPhone) {
        this.workPhone = workPhone;
        return this;
    }

    public void setWorkPhone(String workPhone) {
        this.workPhone = workPhone;
    }

    public WorkLocationType getWorkLocation() {
        return workLocation;
    }

    public Employee workLocation(WorkLocationType workLocation) {
        this.workLocation = workLocation;
        return this;
    }

    public void setWorkLocation(WorkLocationType workLocation) {
        this.workLocation = workLocation;
    }

    public BigDecimal getFte() {
        return fte;
    }

    public Employee fte(BigDecimal fte) {
        this.fte = fte;
        return this;
    }

    public void setFte(BigDecimal fte) {
        this.fte = fte;
    }

    public LocalDate getProbationEndDate() {
        return probationEndDate;
    }

    public Employee probationEndDate(LocalDate probationEndDate) {
        this.probationEndDate = probationEndDate;
        return this;
    }

    public void setProbationEndDate(LocalDate probationEndDate) {
        this.probationEndDate = probationEndDate;
    }

    public LocalDate getConfirmationDate() {
        return confirmationDate;
    }

    public Employee confirmationDate(LocalDate confirmationDate) {
        this.confirmationDate = confirmationDate;
        return this;
    }

    public void setConfirmationDate(LocalDate confirmationDate) {
        this.confirmationDate = confirmationDate;
    }

    public String getTerminationNotes() {
        return terminationNotes;
    }

    public Employee terminationNotes(String terminationNotes) {
        this.terminationNotes = terminationNotes;
        return this;
    }

    public void setTerminationNotes(String terminationNotes) {
        this.terminationNotes = terminationNotes;
    }

    public String getNationalId() {
        return nationalId;
    }

    public Employee nationalId(String nationalId) {
        this.nationalId = nationalId;
        return this;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getPassportNumber() {
        return passportNumber;
    }

    public Employee passportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
        return this;
    }

    public void setPassportNumber(String passportNumber) {
        this.passportNumber = passportNumber;
    }

    public String getTaxNumber() {
        return taxNumber;
    }

    public Employee taxNumber(String taxNumber) {
        this.taxNumber = taxNumber;
        return this;
    }

    public void setTaxNumber(String taxNumber) {
        this.taxNumber = taxNumber;
    }

    public String getSocialSecurityNumber() {
        return socialSecurityNumber;
    }

    public Employee socialSecurityNumber(String socialSecurityNumber) {
        this.socialSecurityNumber = socialSecurityNumber;
        return this;
    }

    public void setSocialSecurityNumber(String socialSecurityNumber) {
        this.socialSecurityNumber = socialSecurityNumber;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public Employee maritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
        return this;
    }

    public void setMaritalStatus(MaritalStatus maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public Employee employmentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
        return this;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public JobGrade getGrade() {
        return grade;
    }

    public Employee grade(JobGrade grade) {
        this.grade = grade;
        return this;
    }

    public void setGrade(JobGrade grade) {
        this.grade = grade;
    }

    public JobLevel getLevel() {
        return level;
    }

    public Employee level(JobLevel level) {
        this.level = level;
        return this;
    }

    public void setLevel(JobLevel level) {
        this.level = level;
    }

    public EmployeeCategory getCategory() {
        return category;
    }

    public Employee category(EmployeeCategory category) {
        this.category = category;
        return this;
    }

    public void setCategory(EmployeeCategory category) {
        this.category = category;
    }

    public TerminationReason getTerminationReason() {
        return terminationReason;
    }

    public Employee terminationReason(TerminationReason terminationReason) {
        this.terminationReason = terminationReason;
        return this;
    }

    public void setTerminationReason(TerminationReason terminationReason) {
        this.terminationReason = terminationReason;
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
        return Objects.equals(getId(), employee.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return (
            "Employee{" +
            "id=" +
            getId() +
            ", employeeNumber='" +
            employeeNumber +
            '\'' +
            ", jobTitle='" +
            jobTitle +
            '\'' +
            ", phone='" +
            phone +
            '\'' +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            ", status=" +
            status +
            ", path='" +
            path +
            '\'' +
            '}'
        );
    }
}
