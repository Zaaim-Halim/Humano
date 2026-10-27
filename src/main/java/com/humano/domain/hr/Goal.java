package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * An employee's goal: what they aim to achieve by its due date, how success is measured, and how
 * much it weighs among their goals. The employee proposes goals and their manager agrees them (a goal
 * the manager sets is agreed at once); progress is checked in along the way. The review whose period
 * covers the due date rates it — after which the goal can't change.
 */
@Entity
@Table(name = "goal", indexes = @Index(name = "idx_goal_employee_due", columnList = "employee_id, due_date"))
public class Goal extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    /** How success is measured (the key results). */
    @Column(name = "success_measure", length = 1000)
    private String successMeasure;

    /** Share of the employee's goals, in percent; optional. */
    @Column(name = "weight")
    private Integer weight;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoalStatus status = GoalStatus.DRAFT;

    /** 0–100, from the latest check-in. */
    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "agreed_at")
    private Instant agreedAt;

    /** Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agreed_by_id")
    private Employee agreedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

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

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSuccessMeasure() {
        return successMeasure;
    }

    public void setSuccessMeasure(String successMeasure) {
        this.successMeasure = successMeasure;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public void setStatus(GoalStatus status) {
        this.status = status;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public Instant getAgreedAt() {
        return agreedAt;
    }

    public void setAgreedAt(Instant agreedAt) {
        this.agreedAt = agreedAt;
    }

    public Employee getAgreedBy() {
        return agreedBy;
    }

    public void setAgreedBy(Employee agreedBy) {
        this.agreedBy = agreedBy;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Goal that = (Goal) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "Goal{" + "id=" + id + ", title='" + title + '\'' + ", status=" + status + '}';
    }
}
