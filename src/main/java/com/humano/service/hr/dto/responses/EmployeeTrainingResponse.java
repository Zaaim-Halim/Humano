package com.humano.service.hr.dto.responses;

import com.humano.domain.enumeration.hr.TrainingStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for returning employee training information.
 */
public record EmployeeTrainingResponse(
    UUID id,
    UUID employeeId,
    String employeeName,
    UUID trainingId,
    String trainingName,
    TrainingStatus status,
    String description,
    LocalDate completionDate,
    String feedback
) {}
