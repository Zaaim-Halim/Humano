package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.EmployeeProcessType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight list-row DTO for onboarding/offboarding processes.
 */
public record EmployeeProcessSummary(
    UUID processId,
    EmployeeProcessType processType,
    EmployeeProcessStatus status,
    UUID employeeId,
    String employeeName,
    LocalDate startDate,
    LocalDate dueDate,
    LocalDate completionDate,
    int completionPercentage,
    int totalTasks,
    int completedTasks,
    /** Open tasks whose due date has passed. */
    int overdueTasks,
    /** Process notes; on a cancelled process, the cancellation reason. */
    String notes,
    Instant lastModifiedDate
) {}
