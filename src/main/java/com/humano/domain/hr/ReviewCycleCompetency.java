package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A competency as the cycle's template had it when the cycle opened — a copy, so later edits to the
 * framework never change what this cycle's reviewers rated against. Never updated.
 */
@Entity
@Table(name = "review_cycle_competency", indexes = @Index(name = "idx_review_cycle_competency_cycle", columnList = "cycle_id, sort_order"))
public class ReviewCycleCompetency extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, updatable = false)
    private ReviewCycle cycle;

    @Column(name = "sort_order", nullable = false, updatable = false)
    private int sortOrder;

    @Column(name = "name", nullable = false, length = 100, updatable = false)
    private String name;

    @Column(name = "description", length = 1000, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20, updatable = false)
    private CompetencyCategory category;

    @Embedded
    private RatingAnchors anchors = new RatingAnchors();

    public ReviewCycleCompetency() {}

    /** The snapshot of {@code competency} for {@code cycle}, at {@code sortOrder}. */
    public static ReviewCycleCompetency of(ReviewCycle cycle, Competency competency, int sortOrder) {
        ReviewCycleCompetency copy = new ReviewCycleCompetency();
        copy.cycle = cycle;
        copy.sortOrder = sortOrder;
        copy.name = competency.getName();
        copy.description = competency.getDescription();
        copy.category = competency.getCategory();
        copy.anchors = competency.getAnchors().copy();
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

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CompetencyCategory getCategory() {
        return category;
    }

    public RatingAnchors getAnchors() {
        return anchors != null ? anchors : new RatingAnchors();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewCycleCompetency that = (ReviewCycleCompetency) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewCycleCompetency{" + "id=" + id + ", name='" + name + '\'' + '}';
    }
}
