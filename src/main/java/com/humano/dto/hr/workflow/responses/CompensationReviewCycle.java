package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A regular review cycle, to pick a compensation review from.
 */
public record CompensationReviewCycle(
    UUID id,
    String name,
    ReviewCyclePhase phase,
    LocalDate reviewPeriodStart,
    LocalDate reviewPeriodEnd,
    long proposals
) {}
