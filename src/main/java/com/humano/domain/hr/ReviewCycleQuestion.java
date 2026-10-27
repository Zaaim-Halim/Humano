package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A question as the cycle's template had it when the cycle opened — a copy, so later edits to the
 * template never change what this cycle's people answered. Never updated.
 */
@Entity
@Table(name = "review_cycle_question", indexes = @Index(name = "idx_review_cycle_question_cycle", columnList = "cycle_id, sort_order"))
public class ReviewCycleQuestion extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, updatable = false)
    private ReviewCycle cycle;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Embedded
    private ReviewQuestionDefinition definition;

    public ReviewCycleQuestion() {}

    /** The snapshot of {@code definition} for {@code cycle}, at {@code sortOrder}. */
    public static ReviewCycleQuestion of(ReviewCycle cycle, ReviewQuestionDefinition definition, int sortOrder) {
        ReviewCycleQuestion copy = new ReviewCycleQuestion();
        copy.cycle = cycle;
        copy.sortOrder = sortOrder;
        copy.definition = definition.copy();
        return copy;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ReviewCycle getCycle() {
        return cycle;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ReviewQuestionDefinition getDefinition() {
        return definition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewCycleQuestion that = (ReviewCycleQuestion) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewCycleQuestion{" + "id=" + id + ", sortOrder=" + sortOrder + '}';
    }
}
