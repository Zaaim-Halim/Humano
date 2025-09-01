package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a timesheet entry for an employee, recording hours worked on a specific date and project.
 * <p>
 * Used to track employee work hours for payroll and project management.
 */
@Entity
@Table(name = "timesheet")
public class Timesheet extends AbstractAuditingEntity<UUID> {

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
     * Date of the timesheet entry.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Number of hours worked on the date.
     */
    @Column(name = "hours_worked", nullable = false)
    private BigDecimal hoursWorked;

    /**
     * Project associated with the timesheet entry.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /**
     * The employee who worked the hours.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Getters and setters
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public Timesheet date(LocalDate date) {
        this.date = date;
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getHoursWorked() {
        return hoursWorked;
    }

    public Timesheet hoursWorked(BigDecimal hoursWorked) {
        this.hoursWorked = hoursWorked;
        return this;
    }

    public void setHoursWorked(BigDecimal hoursWorked) {
        this.hoursWorked = hoursWorked;
    }

    public Project getProject() {
        return project;
    }

    public Timesheet project(Project project) {
        this.project = project;
        return this;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Timesheet employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Timesheet timesheet = (Timesheet) o;
        return Objects.equals(id, timesheet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Timesheet{" +
            "id=" + id +
            ", date=" + date +
            ", hoursWorked=" + hoursWorked +
            '}';
    }
}
