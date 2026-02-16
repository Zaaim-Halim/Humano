package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.TrainingStatus;
import com.humano.domain.enumeration.hr.WorkflowStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TrainingEnrollmentResponse(
    UUID enrollmentId,
    UUID workflowId,
    UUID employeeId,
    String employeeName,
    UUID trainingId,
    String trainingName,
    String trainingProvider,
    LocalDate trainingStartDate,
    LocalDate trainingEndDate,
    WorkflowStatus workflowStatus,
    TrainingStatus enrollmentStatus,
    boolean approvalRequired,
    UUID approverId,
    String approverName,
    String approvalComments,
    Instant enrolledAt,
    Instant approvedAt,
    LocalDate completionDate,
    String feedback,
    Instant createdDate,
    Instant lastModifiedDate
) {}
