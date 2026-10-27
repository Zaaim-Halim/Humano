package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The reviewer's rating of one of the cycle's competencies in a review, with an optional comment.
 * Shown to the employee once the review is shared, like the overall rating.
 */
@Entity
@Table(
    name = "review_competency_rating",
    uniqueConstraints = @UniqueConstraint(name = "ux_review_competency_rating", columnNames = { "review_id", "competency_id" })
)
public class ReviewCompetencyRating extends AbstractAuditingEntity<UUID> {

    public static final int COMMENT_LENGTH = 2000;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competency_id", nullable = false, updatable = false)
    private ReviewCycleCompetency competency;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", length = COMMENT_LENGTH)
    private String comment;

    public ReviewCompetencyRating() {}

    public ReviewCompetencyRating(PerformanceReview review, ReviewCycleCompetency competency) {
        this.review = review;
        this.competency = competency;
    }

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

    public ReviewCycleCompetency getCompetency() {
        return competency;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewCompetencyRating that = (ReviewCompetencyRating) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewCompetencyRating{" + "id=" + id + ", rating=" + rating + '}';
    }
}
