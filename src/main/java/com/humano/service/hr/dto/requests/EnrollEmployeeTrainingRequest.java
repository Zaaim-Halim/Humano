package com.humano.service.hr.dto.requests;

import com.humano.domain.enumeration.hr.TrainingStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO record for enrolling an employee in a training program.
 */
public record EnrollEmployeeTrainingRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Training ID is required") UUID trainingId,

    TrainingStatus status,

    String description
) {}
