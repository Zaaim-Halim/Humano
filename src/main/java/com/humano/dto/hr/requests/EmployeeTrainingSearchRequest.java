package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.TrainingStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for searching employee training records with multiple criteria.
 * All fields are optional and will be combined with AND logic.
 */
public record EmployeeTrainingSearchRequest(
    UUID employeeId,
    UUID trainingId,
    TrainingStatus status,
    LocalDate completionDateFrom,
    LocalDate completionDateTo,
    String description,
    String feedback
) {}
