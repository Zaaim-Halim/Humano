package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * An answer to one of a cycle's template questions. A blank answer removes an earlier one.
 */
public record ReviewAnswerInput(@NotNull UUID questionId, @Size(max = 10000) String answer) {}
