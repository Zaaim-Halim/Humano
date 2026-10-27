package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Delete the cycles due under the retention period now — exactly the ones the caller was shown, so a
 * list that changed meanwhile is refused rather than deleting something nobody saw.
 */
public record PurgeReviewCyclesRequest(@NotEmpty @Size(max = 500) List<@NotNull UUID> cycleIds) {}
