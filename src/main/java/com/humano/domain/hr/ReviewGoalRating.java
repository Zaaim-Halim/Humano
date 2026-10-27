package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * The reviewer's view of one goal in a review — written when the review is submitted, with the goal's
 * title and weight as they were then, so the review keeps saying what was rated. A goal rated in a
 * submitted review can no longer change. Shown to the employee once the review is shared.
 */
@Entity
@Table(
    name = "review_goal_rating",
    uniqueConstraints = @UniqueConstraint(name = "ux_review_goal_rating", columnNames = { "review_id", "goal_id" })
)
public class ReviewGoalRating extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false, updatable = false)
    private Goal goal;

    @Column(name = "goal_title", nullable = false, length = 200)
    private String goalTitle;

    @Column(name = "goal_weight")
    private Integer goalWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement", nullable = false, length = 20)
    private GoalAchievement achievement;

    @Column(name = "comment", length = 2000)
    private String comment;

    public ReviewGoalRating() {}

    public ReviewGoalRating(PerformanceReview review, Goal goal) {
        this.review = review;
        this.goal = goal;
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

    public Goal getGoal() {
        return goal;
    }

    public String getGoalTitle() {
        return goalTitle;
    }

    public void setGoalTitle(String goalTitle) {
        this.goalTitle = goalTitle;
    }

    public Integer getGoalWeight() {
        return goalWeight;
    }

    public void setGoalWeight(Integer goalWeight) {
        this.goalWeight = goalWeight;
    }

    public GoalAchievement getAchievement() {
        return achievement;
    }

    public void setAchievement(GoalAchievement achievement) {
        this.achievement = achievement;
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
        ReviewGoalRating that = (ReviewGoalRating) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewGoalRating{" + "id=" + id + ", achievement=" + achievement + '}';
    }
}
