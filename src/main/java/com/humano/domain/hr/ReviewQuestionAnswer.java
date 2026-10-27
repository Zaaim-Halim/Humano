package com.humano.domain.hr;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * An answer to one of the cycle's questions in a review — the employee's, in their
 * self-assessment, or the reviewer's, which the employee reads once the review is shared.
 */
@Entity
@Table(
    name = "review_question_answer",
    uniqueConstraints = @UniqueConstraint(name = "ux_review_question_answer", columnNames = { "review_id", "question_id" })
)
public class ReviewQuestionAnswer extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, updatable = false)
    private ReviewCycleQuestion question;

    @Lob
    @Column(name = "answer", nullable = false)
    private String answer;

    public ReviewQuestionAnswer() {}

    public ReviewQuestionAnswer(PerformanceReview review, ReviewCycleQuestion question) {
        this.review = review;
        this.question = question;
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

    public ReviewCycleQuestion getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReviewQuestionAnswer that = (ReviewQuestionAnswer) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "ReviewQuestionAnswer{" + "id=" + id + '}';
    }
}
