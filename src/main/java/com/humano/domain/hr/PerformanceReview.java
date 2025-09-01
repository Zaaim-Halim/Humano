package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a performance review for an employee, including review date, comments, rating, reviewer, and employee.
 * <p>
 * Used to track employee performance evaluations and feedback.
 */
@Entity
@Table(name = "performance_review")
public class PerformanceReview extends AbstractAuditingEntity<UUID> {
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
     * Date the review was conducted.
     */
    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    /**
     * Comments provided during the review.
     */
    @Lob
    @Column(name = "comments")
    private String comments;

    /**
     * Rating given during the review (e.g., 1-5).
     */
    @Column(name = "rating", columnDefinition = "default 0")
    @Min(0)
    @Max(5)
    private Integer rating;

    /**
     * The employee who conducted the review.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private Employee reviewer;

    /**
     * The employee being reviewed.
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

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public PerformanceReview reviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
        return this;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public String getComments() {
        return comments;
    }

    public PerformanceReview comments(String comments) {
        this.comments = comments;
        return this;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Integer getRating() {
        return rating;
    }

    public PerformanceReview rating(Integer rating) {
        this.rating = rating;
        return this;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Employee getReviewer() {
        return reviewer;
    }

    public PerformanceReview reviewer(Employee reviewer) {
        this.reviewer = reviewer;
        return this;
    }

    public void setReviewer(Employee reviewer) {
        this.reviewer = reviewer;
    }

    public Employee getEmployee() {
        return employee;
    }

    public PerformanceReview employee(Employee employee) {
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
        PerformanceReview that = (PerformanceReview) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PerformanceReview{" +
            "id=" + id +
            ", reviewDate=" + reviewDate +
            ", rating=" + rating +
            '}';
    }
}
