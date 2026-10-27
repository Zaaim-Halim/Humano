package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One objective of an improvement plan: what is expected, how it is measured, and optionally by when.
 */
@Entity
@Table(
    name = "improvement_plan_objective",
    indexes = @Index(name = "idx_improvement_plan_objective_plan", columnList = "plan_id, sort_order")
)
public class ImprovementPlanObjective extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private ImprovementPlan plan;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "success_measure", length = 1000)
    private String successMeasure;

    @Column(name = "due_date")
    private LocalDate dueDate;

    public ImprovementPlanObjective() {}

    public ImprovementPlanObjective(ImprovementPlan plan, int sortOrder, String description, String successMeasure, LocalDate dueDate) {
        this.plan = plan;
        this.sortOrder = sortOrder;
        this.description = description;
        this.successMeasure = successMeasure;
        this.dueDate = dueDate;
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

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImprovementPlanObjective that = (ImprovementPlanObjective) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
