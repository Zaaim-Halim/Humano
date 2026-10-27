package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.ImprovementPlanProgress;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A progress review in an improvement plan, recorded by the manager or HR and seen by the employee.
 * Append-only: the plan's record.
 */
@Entity
@Table(
    name = "improvement_plan_check_in",
    indexes = @Index(name = "idx_improvement_plan_check_in_plan", columnList = "plan_id, checked_in_at")
)
public class ImprovementPlanCheckIn extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private ImprovementPlan plan;

    /** Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", updatable = false)
    private Employee author;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress", nullable = false, length = 20, updatable = false)
    private ImprovementPlanProgress progress;

    @Lob
    @Column(name = "note", nullable = false, updatable = false)
    private String note;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;

    public ImprovementPlanCheckIn() {}

    public ImprovementPlanCheckIn(
        ImprovementPlan plan,
        Employee author,
        ImprovementPlanProgress progress,
        String note,
        Instant checkedInAt
    ) {
        this.plan = plan;
        this.author = author;
        this.progress = progress;
        this.note = note;
        this.checkedInAt = checkedInAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ImprovementPlan getPlan() {
        return plan;
    }

    public void setPlan(ImprovementPlan plan) {
        this.plan = plan;
    }

    public Employee getAuthor() {
        return author;
    }

    public void setAuthor(Employee author) {
        this.author = author;
    }

    public ImprovementPlanProgress getProgress() {
        return progress;
    }

    public void setProgress(ImprovementPlanProgress progress) {
        this.progress = progress;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(Instant checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImprovementPlanCheckIn that = (ImprovementPlanCheckIn) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
