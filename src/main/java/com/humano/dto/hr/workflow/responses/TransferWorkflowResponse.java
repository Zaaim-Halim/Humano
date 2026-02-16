package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.WorkflowStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for transfer workflow details.
 */
public record TransferWorkflowResponse(
    UUID transferId,
    UUID workflowId,
    UUID employeeId,
    String employeeName,
    WorkflowStatus status,
    String currentState,
    TransferDetails currentDetails,
    TransferDetails newDetails,
    LocalDate effectiveDate,
    String reason,
    ApprovalStatus approvalStatus,
    Instant createdDate,
    Instant lastModifiedDate
) {
    public record TransferDetails(
        UUID departmentId,
        String departmentName,
        UUID positionId,
        String positionTitle,
        UUID managerId,
        String managerName,
        UUID organizationalUnitId,
        String organizationalUnitName
    ) {}

    public record ApprovalStatus(
        boolean currentManagerApproved,
        String currentManagerComments,
        Instant currentManagerDecisionAt,
        boolean newManagerApproved,
        String newManagerComments,
        Instant newManagerDecisionAt,
        boolean hrApproved,
        String hrComments,
        Instant hrDecisionAt
    ) {}
}
