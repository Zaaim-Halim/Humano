package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Represents a skill or competency possessed by an employee.
 * <p>
 * Used to track employee competencies, proficiency levels, and skill-based assignments.
 */
@Entity
@Table(name = "employee_skill")
public class EmployeeSkill extends AbstractAuditingEntity<UUID> {

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
     * The employee who possesses this skill.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The skill reference.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    /**
     * The proficiency level (1-5).
     */
    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "proficiency_level", nullable = false)
    private Integer proficiencyLevel;

    /**
     * Date the skill was acquired or last verified.
     */
    @Column(name = "acquisition_date")
    private LocalDate acquisitionDate;

    /**
     * Date the skill certification expires, if applicable.
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * Additional notes about the employee's skill.
     */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Whether this skill has been verified by management.
     */
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeSkill employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Skill getSkill() {
        return skill;
    }

    public EmployeeSkill skill(Skill skill) {
        this.skill = skill;
        return this;
    }

    public void setSkill(Skill skill) {
        this.skill = skill;
    }

    public Integer getProficiencyLevel() {
        return proficiencyLevel;
    }

    public EmployeeSkill proficiencyLevel(Integer proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
        return this;
    }

    public void setProficiencyLevel(Integer proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
    }

    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public EmployeeSkill acquisitionDate(LocalDate acquisitionDate) {
        this.acquisitionDate = acquisitionDate;
        return this;
    }

    public void setAcquisitionDate(LocalDate acquisitionDate) {
        this.acquisitionDate = acquisitionDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public EmployeeSkill expiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
        return this;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getNotes() {
        return notes;
    }

    public EmployeeSkill notes(String notes) {
        this.notes = notes;
        return this;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getVerified() {
        return isVerified;
    }

    public EmployeeSkill verified(Boolean verified) {
        isVerified = verified;
        return this;
    }

    public void setVerified(Boolean verified) {
        isVerified = verified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeSkill that = (EmployeeSkill) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "EmployeeSkill{" +
            "id=" +
            id +
            ", proficiencyLevel=" +
            proficiencyLevel +
            ", acquisitionDate=" +
            acquisitionDate +
            ", expiryDate=" +
            expiryDate +
            ", isVerified=" +
            isVerified +
            '}'
        );
    }
}
