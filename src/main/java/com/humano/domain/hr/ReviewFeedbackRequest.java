package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.FeedbackRelationship;
import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A request for feedback on a cycle review from someone other than the reviewer: a peer, a direct
 * report, a project lead... The reviewer or HR asks directly; the reviewed employee may nominate
 * contributors, which the reviewer approves first. The feedback is narrative (no rating) and is
 * input for the reviewer; whether the employee sees it is the cycle's
 * {@link ReviewCycle#getFeedbackVisibility() feedback visibility}.
 */
@Entity
@Table(
    name = "review_feedback_request",
    indexes = {
        @Index(name = "idx_review_feedback_request_review", columnList = "review_id, status"),
        @Index(name = "idx_review_feedback_request_contributor", columnList = "contributor_id, status"),
    }
)
public class ReviewFeedbackRequest extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private PerformanceReview review;

    /** The employee asked for feedback. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contributor_id", nullable = false)
    private Employee contributor;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship", nullable = false, length = 30)
    private FeedbackRelationship relationship;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FeedbackRequestStatus status;

    /** Who asked: the reviewer, HR, or the reviewed employee (a nomination). Empty for a user who isn't an employee. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id")
    private Employee requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** A note to the contributor: what to focus on. */
    @Column(name = "message", length = 1000)
    private String message;

    @Lob
    @Column(name = "strengths")
    private String strengths;

    @Lob
    @Column(name = "improvements")
    private String improvements;

    @Lob
    @Column(name = "comments")
    private String comments;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Why the contributor declined, or why the nomination was turned down. */
    @Column(name = "decline_reason", length = 500)
    private String declineReason;

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

    public Employee getContributor() {
        return contributor;
    }

    public void setContributor(Employee contributor) {
        this.contributor = contributor;
    }

    public FeedbackRelationship getRelationship() {
        return relationship;
    }

    public void setRelationship(FeedbackRelationship relationship) {
        this.relationship = relationship;
    }

    public FeedbackRequestStatus getStatus() {
        return status;
    }

    public void setStatus(FeedbackRequestStatus status) {
        this.status = status;
    }

    public Employee getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Employee requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getImprovements() {
        return improvements;
    }

    public void setImprovements(String improvements) {
        this.improvements = improvements;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewFeedbackRequest that = (ReviewFeedbackRequest) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewFeedbackRequest{" + "id=" + id + ", relationship=" + relationship + ", status=" + status + '}';
    }
}
