package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.CompensationProposalStatus;
import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A compensation proposal. It carries the review's final rating and promotion recommendation, never
 * the review's text.
 *
 * @param increasePercent  the base pay increase in percent; empty without a current or new salary
 * @param guidanceMin      the merit matrix's lower bound for the rating, in percent
 * @param guidanceMax      the merit matrix's upper bound for the rating, in percent
 * @param outsideGuidance  whether the increase falls outside that range (a flag, never a block)
 * @param payChanged       whether the pay or position in effect changed since the proposal was prepared;
 *                         it has to be reopened and saved again before it can be applied
 */
public record CompensationProposalResponse(
    UUID id,
    UUID cycleId,
    UUID reviewId,
    UUID employeeId,
    String employeeName,
    String jobTitle,
    int rating,
    boolean promotionRecommended,
    CompensationProposalStatus status,
    LocalDate effectiveDate,
    BigDecimal currentSalary,
    String currencyCode,
    Basis basis,
    UUID currentPositionId,
    String currentPositionName,
    BigDecimal newSalary,
    BigDecimal increasePercent,
    BigDecimal guidanceMin,
    BigDecimal guidanceMax,
    boolean outsideGuidance,
    UUID newPositionId,
    String newPositionName,
    BigDecimal bonusAmount,
    String justification,
    String returnComment,
    String submittedByName,
    Instant submittedAt,
    String approvedByName,
    Instant approvedAt,
    String appliedByName,
    Instant appliedAt,
    boolean payChanged,
    Actions actions
) {
    /** What the signed-in user may do with it now. */
    public record Actions(boolean edit, boolean delete, boolean submit, boolean approve, boolean returnToDraft, boolean apply) {}
}
