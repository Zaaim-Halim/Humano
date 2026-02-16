package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for onboarding process details.
 */
public record OnboardingProcessResponse(
    UUID processId,
    UUID workflowId,
    UUID employeeId,
    String employeeName,
    String employeeEmail,
    WorkflowStatus workflowStatus,
    EmployeeProcessStatus processStatus,
    String currentState,
    LocalDate startDate,
    LocalDate dueDate,
    int completionPercentage,
    List<OnboardingTaskResponse> tasks,
    OnboardingProgress progress,
    Instant createdDate,
    Instant lastModifiedDate
) {
    public record OnboardingTaskResponse(
        UUID taskId,
        String title,
        String description,
        LocalDate dueDate,
        boolean completed,
        LocalDate completionDate,
        UUID assignedToId,
        String assignedToName,
        String completionNotes
    ) {}

    public record OnboardingProgress(
        boolean profileCreated,
        boolean departmentAssigned,
        boolean benefitsSetup,
        boolean trainingsAssigned,
        boolean documentsUploaded,
        boolean systemAccessGranted,
        int totalTasks,
        int completedTasks
    ) {}
}
