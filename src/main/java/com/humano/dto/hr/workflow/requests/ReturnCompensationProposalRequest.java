package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Sends a submitted or approved proposal back to draft.
 *
 * @param comment what to change, for whoever prepares it
 */
public record ReturnCompensationProposalRequest(@NotBlank @Size(max = 500) String comment) {}
