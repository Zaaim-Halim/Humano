package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.RatingChangeSource;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * One change to a review's final rating after the reviewer gave it — in calibration or when a
 * dispute was resolved — with who changed it and why. Append-only: the audit trail HR reads.
 */
@Entity
@Table(name = "review_rating_change", indexes = @Index(name = "idx_review_rating_change_review", columnList = "review_id, changed_at"))
public class ReviewRatingChange extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @Column(name = "from_rating", updatable = false)
    private Integer fromRating;

    @Column(name = "to_rating", nullable = false, updatable = false)
    private Integer toRating;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30, updatable = false)
    private RatingChangeSource source;

    @Lob
    @Column(name = "reason", updatable = false)
    private String reason;

    /** Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id", updatable = false)
    private Employee changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PerformanceReview getReview() {
        return review;
    }

    public void setReview(PerformanceReview review) {
        this.review = review;
    }

    public Integer getFromRating() {
        return fromRating;
    }

    public void setFromRating(Integer fromRating) {
        this.fromRating = fromRating;
    }

    public Integer getToRating() {
        return toRating;
    }

    public void setToRating(Integer toRating) {
        this.toRating = toRating;
    }

    public RatingChangeSource getSource() {
        return source;
    }

    public void setSource(RatingChangeSource source) {
        this.source = source;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Employee getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Employee changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewRatingChange that = (ReviewRatingChange) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewRatingChange{" + "id=" + id + ", from=" + fromRating + ", to=" + toRating + ", source=" + source + '}';
    }
}
