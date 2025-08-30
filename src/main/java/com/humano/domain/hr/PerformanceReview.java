package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.LocalDate;
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
    @Column(name = "rating")
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
    public UUID getId() {
        return id;
    }

}
