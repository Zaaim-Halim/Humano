package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * A pay and promotion decision following a regular review cycle, for one reviewed employee: a new base
 * salary, a new position and/or a one-off bonus, from a date — or none. It carries only the review's
 * final rating and promotion recommendation, never its text.
 *
 * <p>It keeps the pay and position in effect when it was last prepared ({@code current*}), so applying
 * it can tell when they changed meanwhile. Drafted by the compensation team, approved by someone else
 * holding the compensation permission than whoever prepared or submitted it, and applied once —
 * through the position-change service and a bonus row, which is when pay actually changes.
 */
@Entity
@Table(
    name = "compensation_proposal",
    uniqueConstraints = @UniqueConstraint(name = "ux_compensation_proposal_review", columnNames = "review_id"),
    indexes = @Index(name = "idx_compensation_proposal_cycle", columnList = "cycle_id, status")
)
public class CompensationProposal extends AbstractAuditingEntity<UUID> {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false, updatable = false)
    private ReviewCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, updatable = false)
    private PerformanceReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false, updatable = false)
    private Employee employee;

    /** The review's final rating when the proposal was made. */
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "promotion_recommended", nullable = false)
    private boolean promotionRecommended;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompensationProposalStatus status = CompensationProposalStatus.DRAFT;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** The base salary in effect on the effective date when last prepared; empty without a salary record. */
    @Column(name = "current_salary", precision = 21, scale = 2)
    private BigDecimal currentSalary;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id")
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "basis", length = 20)
    private Basis basis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_position_id")
    private Position currentPosition;

    /** The proposed base salary, on the same basis and currency; empty for no change. */
    @Column(name = "new_salary", precision = 21, scale = 2)
    private BigDecimal newSalary;

    /** The position promoted to; empty for none. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_position_id")
    private Position newPosition;

    /** A one-off performance bonus in the pay currency; empty for none. */
    @Column(name = "bonus_amount", precision = 21, scale = 2)
    private BigDecimal bonusAmount;

    @Column(name = "justification", length = 1000)
    private String justification;

    /** Why it was sent back to draft, last time. */
    @Column(name = "return_comment", length = 500)
    private String returnComment;

    /** Who last set its content (drafted, edited, or filled from the merit matrix) — they don't approve it. */
    @Column(name = "prepared_by_id")
    private UUID preparedById;

    @Column(name = "submitted_by_id")
    private UUID submittedById;

    @Column(name = "submitted_by_name", length = 100)
    private String submittedByName;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "approved_by_name", length = 100)
    private String approvedByName;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "applied_by_name", length = 100)
    private String appliedByName;

    @Column(name = "applied_at")
    private Instant appliedAt;

    /** The position-history entry applying it wrote, if pay or position changed. */
    @Column(name = "position_change_id")
    private UUID positionChangeId;

    /** The bonus applying it awarded, if any. */
    @Column(name = "bonus_id")
    private UUID bonusId;

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

    public void setCycle(ReviewCycle cycle) {
        this.cycle = cycle;
    }

    public PerformanceReview getReview() {
        return review;
    }

    public void setReview(PerformanceReview review) {
        this.review = review;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public boolean isPromotionRecommended() {
        return promotionRecommended;
    }

    public void setPromotionRecommended(boolean promotionRecommended) {
        this.promotionRecommended = promotionRecommended;
    }

    public CompensationProposalStatus getStatus() {
        return status;
    }

    public void setStatus(CompensationProposalStatus status) {
        this.status = status;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public BigDecimal getCurrentSalary() {
        return currentSalary;
    }

    public void setCurrentSalary(BigDecimal currentSalary) {
        this.currentSalary = currentSalary;
    }

    public Currency getCurrency() {
        return currency;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Basis getBasis() {
        return basis;
    }

    public void setBasis(Basis basis) {
        this.basis = basis;
    }

    public Position getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(Position currentPosition) {
        this.currentPosition = currentPosition;
    }

    public BigDecimal getNewSalary() {
        return newSalary;
    }

    public void setNewSalary(BigDecimal newSalary) {
        this.newSalary = newSalary;
    }

    public Position getNewPosition() {
        return newPosition;
    }

    public void setNewPosition(Position newPosition) {
        this.newPosition = newPosition;
    }

    public BigDecimal getBonusAmount() {
        return bonusAmount;
    }

    public void setBonusAmount(BigDecimal bonusAmount) {
        this.bonusAmount = bonusAmount;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public String getReturnComment() {
        return returnComment;
    }

    public void setReturnComment(String returnComment) {
        this.returnComment = returnComment;
    }

    public UUID getPreparedById() {
        return preparedById;
    }

    public void setPreparedById(UUID preparedById) {
        this.preparedById = preparedById;
    }

    public UUID getSubmittedById() {
        return submittedById;
    }

    public void setSubmittedById(UUID submittedById) {
        this.submittedById = submittedById;
    }

    public String getSubmittedByName() {
        return submittedByName;
    }

    public void setSubmittedByName(String submittedByName) {
        this.submittedByName = submittedByName;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public UUID getApprovedById() {
        return approvedById;
    }

    public void setApprovedById(UUID approvedById) {
        this.approvedById = approvedById;
    }

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String approvedByName) {
        this.approvedByName = approvedByName;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getAppliedByName() {
        return appliedByName;
    }

    public void setAppliedByName(String appliedByName) {
        this.appliedByName = appliedByName;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public UUID getPositionChangeId() {
        return positionChangeId;
    }

    public void setPositionChangeId(UUID positionChangeId) {
        this.positionChangeId = positionChangeId;
    }

    public UUID getBonusId() {
        return bonusId;
    }

    public void setBonusId(UUID bonusId) {
        this.bonusId = bonusId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CompensationProposal that = (CompensationProposal) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
