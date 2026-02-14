package com.humano.dto.hr.requests;

import com.humano.domain.enumeration.hr.TrainingStatus;
import java.time.LocalDate;

/**
 * DTO record for updating an employee's training record.
 */
public record UpdateEmployeeTrainingRequest(
    TrainingStatus status,

    String description,

    LocalDate completionDate,

    String feedback
) {}
