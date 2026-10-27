package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Takes a cycle back one phase. The reason is kept in the cycle's history. The deadlines are new
 * dates for the phase it goes back to and those after it; one left empty keeps its current date.
 * Earlier phases' deadlines are ignored.
 */
public record MoveBackPhaseRequest(
    @NotBlank(message = "Say why the cycle goes back") @Size(max = 500) String reason,
    LocalDate selfAssessmentDeadline,
    LocalDate managerReviewDeadline,
    LocalDate calibrationDeadline,
    LocalDate feedbackDeadline
) {}
