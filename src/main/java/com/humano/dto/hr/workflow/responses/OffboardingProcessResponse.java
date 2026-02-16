package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.EmployeeProcessStatus;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for offboarding process details.
 */
public record OffboardingProcessResponse(
    UUID processId,
    UUID workflowId,
    UUID employeeId,
    String employeeName,
    String employeeEmail,
    WorkflowStatus workflowStatus,
    EmployeeProcessStatus processStatus,
    String currentState,
    LocalDate lastWorkingDate,
    String offboardingReason,
    int completionPercentage,
    List<OffboardingTaskResponse> tasks,
    OffboardingProgress progress,
    FinalSettlementSummary settlement,
    Instant createdDate,
    Instant lastModifiedDate
) {
    public record OffboardingTaskResponse(
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

    public record OffboardingProgress(
        boolean pendingRequestsProcessed,
        boolean settlementCalculated,
        boolean benefitsTerminated,
        boolean knowledgeTransferCompleted,
        boolean assetsReturned,
        boolean exitInterviewConducted,
        boolean accessRevoked,
        int totalTasks,
        int completedTasks
    ) {}

    public record FinalSettlementSummary(
        int remainingLeaveDays,
        BigDecimal leaveEncashment,
        BigDecimal pendingExpenseReimbursements,
        BigDecimal pendingOvertimePay,
        BigDecimal totalSettlement
    ) {}
}
