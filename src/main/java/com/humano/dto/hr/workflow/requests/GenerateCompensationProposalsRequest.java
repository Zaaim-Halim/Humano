package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Drafts a proposal for each eligible review of a cycle.
 *
 * @param effectiveDate the date the drafts take effect, changeable on each
 */
public record GenerateCompensationProposalsRequest(@NotNull LocalDate effectiveDate) {}
