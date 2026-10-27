package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.payroll.Basis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A regular cycle's compensation review: its proposals and their totals.
 *
 * @param eligible     shared, rated reviews without a proposal yet, that drafting would pick up
 * @param waiting      reviews not shared or rated yet
 * @param disputed     shared reviews whose dispute is still open — no proposal until it is resolved
 * @param totals       one line per currency and pay basis, since they can't be added up
 */
public record CompensationReviewResponse(
    UUID cycleId,
    String cycleName,
    ReviewCyclePhase phase,
    LocalDate reviewPeriodStart,
    LocalDate reviewPeriodEnd,
    int eligible,
    int waiting,
    int disputed,
    List<CompensationProposalResponse> proposals,
    List<Total> totals
) {
    /**
     * @param current  the current base pay of the proposals with a salary record
     * @param proposed the same with the proposed salaries
     * @param bonuses  the proposed one-off bonuses
     */
    public record Total(
        String currencyCode,
        Basis basis,
        int count,
        BigDecimal current,
        BigDecimal proposed,
        BigDecimal increasePercent,
        BigDecimal bonuses
    ) {}
}
