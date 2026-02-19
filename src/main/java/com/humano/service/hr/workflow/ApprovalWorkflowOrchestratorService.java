package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.*;
import com.humano.domain.hr.*;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.ApprovalDecisionRequest;
import com.humano.dto.hr.workflow.requests.SubmitApprovalRequest;
import com.humano.dto.hr.workflow.responses.ApprovalWorkflowResponse;
import com.humano.dto.hr.workflow.responses.PendingApprovalSummary;
import com.humano.repository.hr.ExpenseClaimRepository;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.OvertimeRecordRepository;
import com.humano.repository.hr.workflow.ApprovalChainConfigRepository;
import com.humano.repository.hr.workflow.ApprovalRequestRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrator service for managing approval workflows.
 * Handles leave requests, expense claims, overtime requests, and other approval-based workflows.
 */
@Service
@Transactional
public class ApprovalWorkflowOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalWorkflowOrchestratorService.class);

    private static final int DEFAULT_APPROVAL_DAYS = 5;

    private final WorkflowStateManager workflowStateManager;
    private final NotificationOrchestrationService notificationService;
    private final DeadlineMonitorService deadlineMonitorService;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalChainConfigRepository approvalChainConfigRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ExpenseClaimRepository expenseClaimRepository;
    private final OvertimeRecordRepository overtimeRecordRepository;

    public ApprovalWorkflowOrchestratorService(
        WorkflowStateManager workflowStateManager,
        NotificationOrchestrationService notificationService,
        DeadlineMonitorService deadlineMonitorService,
        ApprovalRequestRepository approvalRequestRepository,
        ApprovalChainConfigRepository approvalChainConfigRepository,
        EmployeeRepository employeeRepository,
        LeaveRequestRepository leaveRequestRepository,
        ExpenseClaimRepository expenseClaimRepository,
        OvertimeRecordRepository overtimeRecordRepository
    ) {
        this.workflowStateManager = workflowStateManager;
        this.notificationService = notificationService;
        this.deadlineMonitorService = deadlineMonitorService;
        this.approvalRequestRepository = approvalRequestRepository;
        this.approvalChainConfigRepository = approvalChainConfigRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.expenseClaimRepository = expenseClaimRepository;
        this.overtimeRecordRepository = overtimeRecordRepository;
    }

    // ==================== GENERIC APPROVAL SUBMISSION ====================

    /**
     * Submit a request for approval.
     */
    public ApprovalWorkflowResponse submitForApproval(SubmitApprovalRequest request) {
        log.info("Submitting {} for approval - entity: {}", request.approvalType(), request.entityId());

        // Validate requestor
        Employee requestor = employeeRepository
            .findById(request.requestorId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.requestorId()));

        // Check if pending approval already exists
        if (approvalRequestRepository.existsPendingApproval(request.entityId(), request.approvalType())) {
            throw new BadRequestAlertException("A pending approval already exists for this entity", "approval", "pendingexists");
        }

        // Determine the approval chain
        List<ApprovalChainConfig> approvalChain = getApprovalChain(
            request.approvalType(),
            request.amount(),
            requestor.getDepartment() != null ? requestor.getDepartment().getId() : null
        );

        if (approvalChain.isEmpty()) {
            // No approval chain configured - use default (direct manager)
            approvalChain = createDefaultApprovalChain(request.approvalType());
        }

        // Get the first approver
        Employee firstApprover = determineApprover(approvalChain.get(0), requestor);

        if (firstApprover == null) {
            throw new BadRequestAlertException("No approver found for this request", "approval", "noapprover");
        }

        // Create workflow
        Map<String, Object> context = new HashMap<>();
        context.put("approvalType", request.approvalType().name());
        context.put("entityId", request.entityId().toString());
        context.put("entityType", request.entityType());
        context.put("requestorId", request.requestorId().toString());
        context.put("totalLevels", approvalChain.size());
        if (request.amount() != null) context.put("amount", request.amount());
        if (request.daysCount() != null) context.put("daysCount", request.daysCount());

        WorkflowType workflowType = mapApprovalTypeToWorkflowType(request.approvalType());
        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            workflowType,
            request.entityId(),
            request.entityType(),
            context,
            request.requestorId()
        );

        // Create approval request
        ApprovalRequest approvalRequest = new ApprovalRequest();
        approvalRequest.setWorkflowInstance(workflow);
        approvalRequest.setApprovalType(request.approvalType());
        approvalRequest.setEntityId(request.entityId());
        approvalRequest.setEntityType(request.entityType());
        approvalRequest.setRequestor(requestor);
        approvalRequest.setApprover(firstApprover);
        approvalRequest.setCurrentLevel(1);
        approvalRequest.setStatus(WorkflowStatus.PENDING_APPROVAL);
        approvalRequest.setSubmittedAt(Instant.now());
        approvalRequest.setAmount(request.amount());
        approvalRequest.setDaysCount(request.daysCount());
        approvalRequest.setPriority(request.priority() != null ? request.priority() : 3);

        // Set due date
        Instant dueDate = Instant.now().plus(DEFAULT_APPROVAL_DAYS, ChronoUnit.DAYS);
        approvalRequest.setDueDate(dueDate);

        ApprovalRequest savedRequest = approvalRequestRepository.save(approvalRequest);

        // Start workflow
        workflowStateManager.startWorkflow(workflow.getId());
        workflowStateManager.transitionState(workflow.getId(), "PENDING_LEVEL_1", "Awaiting first level approval");
        workflowStateManager.assignWorkflow(workflow.getId(), firstApprover.getId());

        // Register deadline
        deadlineMonitorService.registerDeadline(
            workflow.getId(),
            "APPROVAL_DECISION",
            "Approval decision required for " + request.entityType(),
            dueDate,
            24, // 24 hours warning
            firstApprover.getId()
        );

        // Notify approver
        notificationService.notifyApprovalRequired(
            firstApprover.getId(),
            getApprovalNotificationTitle(request.approvalType()),
            buildApprovalNotificationMessage(request, requestor),
            request.entityId(),
            request.entityType()
        );

        log.info("Created approval request {} for workflow {}", savedRequest.getId(), workflow.getId());

        return getApprovalStatus(savedRequest.getId());
    }

    /**
     * Process an approval decision.
     */
    public ApprovalWorkflowResponse processApprovalDecision(UUID approvalRequestId, ApprovalDecisionRequest decision) {
        log.info("Processing approval decision for request {}: {}", approvalRequestId, decision.decision());

        ApprovalRequest approvalRequest = approvalRequestRepository
            .findById(approvalRequestId)
            .orElseThrow(() -> EntityNotFoundException.create("ApprovalRequest", approvalRequestId));

        if (approvalRequest.getStatus() != WorkflowStatus.PENDING_APPROVAL) {
            throw new BadRequestAlertException("This approval request has already been processed", "approval", "alreadyprocessed");
        }

        WorkflowInstance workflow = approvalRequest.getWorkflowInstance();

        switch (decision.decision()) {
            case APPROVE -> handleApproval(approvalRequest, workflow, decision.comments());
            case REJECT -> handleRejection(approvalRequest, workflow, decision.comments());
            case REQUEST_MORE_INFO -> handleMoreInfoRequest(approvalRequest, decision.comments());
            case DELEGATE -> throw new BadRequestAlertException("Delegation not implemented yet", "approval", "notimplemented");
        }

        return getApprovalStatus(approvalRequestId);
    }

    /**
     * Withdraw an approval request.
     */
    public void withdrawApprovalRequest(UUID approvalRequestId, String reason) {
        log.info("Withdrawing approval request {}: {}", approvalRequestId, reason);

        ApprovalRequest approvalRequest = approvalRequestRepository
            .findById(approvalRequestId)
            .orElseThrow(() -> EntityNotFoundException.create("ApprovalRequest", approvalRequestId));

        if (approvalRequest.getStatus() != WorkflowStatus.PENDING_APPROVAL) {
            throw new BadRequestAlertException("Only pending approvals can be withdrawn", "approval", "cannotwithdraw");
        }

        approvalRequest.setStatus(WorkflowStatus.CANCELLED);
        approvalRequest.setApproverComments("Withdrawn: " + reason);
        approvalRequestRepository.save(approvalRequest);

        // Cancel workflow
        if (approvalRequest.getWorkflowInstance() != null) {
            workflowStateManager.cancelWorkflow(approvalRequest.getWorkflowInstance().getId(), reason);
        }

        // Notify approver
        notificationService.notifyApprovalDecision(
            approvalRequest.getApprover().getId(),
            "Approval Request Withdrawn",
            "The approval request from " + approvalRequest.getRequestor().getFirstName() + " has been withdrawn. Reason: " + reason,
            approvalRequest.getEntityId(),
            approvalRequest.getEntityType(),
            false
        );

        log.info("Withdrawn approval request {}", approvalRequestId);
    }

    /**
     * Get approval status.
     */
    @Transactional(readOnly = true)
    public ApprovalWorkflowResponse getApprovalStatus(UUID approvalRequestId) {
        ApprovalRequest approvalRequest = approvalRequestRepository
            .findById(approvalRequestId)
            .orElseThrow(() -> EntityNotFoundException.create("ApprovalRequest", approvalRequestId));

        return mapToApprovalResponse(approvalRequest);
    }

    /**
     * Get pending approvals for an approver.
     */
    @Transactional(readOnly = true)
    public List<PendingApprovalSummary> getPendingApprovalsForApprover(UUID approverId) {
        List<ApprovalRequest> pendingApprovals = approvalRequestRepository.findByApproverIdAndStatus(
            approverId,
            WorkflowStatus.PENDING_APPROVAL
        );

        return pendingApprovals.stream().map(this::mapToPendingApprovalSummary).collect(Collectors.toList());
    }

    /**
     * Get pending approvals for an approver with pagination.
     */
    @Transactional(readOnly = true)
    public Page<PendingApprovalSummary> getPendingApprovalsForApprover(UUID approverId, Pageable pageable) {
        return approvalRequestRepository
            .findByApproverIdAndStatus(approverId, WorkflowStatus.PENDING_APPROVAL, pageable)
            .map(this::mapToPendingApprovalSummary);
    }

    /**
     * Get approvals by requestor.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalWorkflowResponse> getApprovalsByRequestor(UUID requestorId, Pageable pageable) {
        return approvalRequestRepository.findByRequestorId(requestorId, pageable).map(this::mapToApprovalResponse);
    }

    /**
     * Count pending approvals for an approver.
     */
    @Transactional(readOnly = true)
    public long countPendingApprovals(UUID approverId) {
        return approvalRequestRepository.countByApproverIdAndStatus(approverId, WorkflowStatus.PENDING_APPROVAL);
    }

    /**
     * Bulk approve requests.
     */
    public List<ApprovalWorkflowResponse> bulkApprove(List<UUID> approvalRequestIds, String comments) {
        log.info("Bulk approving {} requests", approvalRequestIds.size());

        List<ApprovalWorkflowResponse> results = new ArrayList<>();
        for (UUID requestId : approvalRequestIds) {
            try {
                ApprovalWorkflowResponse result = processApprovalDecision(
                    requestId,
                    new ApprovalDecisionRequest(ApprovalDecisionRequest.ApprovalDecision.APPROVE, comments)
                );
                results.add(result);
            } catch (Exception e) {
                log.error("Failed to approve request {}: {}", requestId, e.getMessage());
            }
        }

        return results;
    }

    /**
     * Escalate an approval to the next level.
     */
    public void escalateToNextApprover(UUID approvalRequestId) {
        log.info("Escalating approval request {}", approvalRequestId);

        ApprovalRequest approvalRequest = approvalRequestRepository
            .findById(approvalRequestId)
            .orElseThrow(() -> EntityNotFoundException.create("ApprovalRequest", approvalRequestId));

        Employee currentApprover = approvalRequest.getApprover();
        Employee escalationTarget = currentApprover.getManager();

        if (escalationTarget == null) {
            throw new BadRequestAlertException("No escalation target available", "approval", "noescalation");
        }

        approvalRequest.setApprover(escalationTarget);
        approvalRequestRepository.save(approvalRequest);

        // Update workflow
        if (approvalRequest.getWorkflowInstance() != null) {
            workflowStateManager.updateStatus(
                approvalRequest.getWorkflowInstance().getId(),
                WorkflowStatus.ESCALATED,
                "Escalated to " + escalationTarget.getFirstName() + " " + escalationTarget.getLastName()
            );
            workflowStateManager.assignWorkflow(approvalRequest.getWorkflowInstance().getId(), escalationTarget.getId());
        }

        // Notify new approver
        notificationService.notifyEscalation(
            escalationTarget.getId(),
            "Escalated Approval Request",
            "An approval request has been escalated to you from " + currentApprover.getFirstName(),
            approvalRequest.getEntityId(),
            approvalRequest.getEntityType(),
            1
        );

        log.info("Escalated approval request {} to {}", approvalRequestId, escalationTarget.getId());
    }

    // ==================== LEAVE-SPECIFIC METHODS ====================

    /**
     * Submit a leave request for approval.
     */
    public ApprovalWorkflowResponse submitLeaveRequest(UUID leaveRequestId) {
        LeaveRequest leaveRequest = leaveRequestRepository
            .findById(leaveRequestId)
            .orElseThrow(() -> EntityNotFoundException.create("LeaveRequest", leaveRequestId));

        return submitForApproval(
            new SubmitApprovalRequest(
                ApprovalType.LEAVE_REQUEST,
                leaveRequestId,
                "LeaveRequest",
                leaveRequest.getEmployee().getId(),
                null,
                leaveRequest.getDaysCount(),
                3,
                "Leave request: " +
                leaveRequest.getLeaveType() +
                " from " +
                leaveRequest.getStartDate() +
                " to " +
                leaveRequest.getEndDate()
            )
        );
    }

    // ==================== EXPENSE-SPECIFIC METHODS ====================

    /**
     * Submit an expense claim for approval.
     */
    public ApprovalWorkflowResponse submitExpenseClaim(UUID expenseClaimId) {
        ExpenseClaim expenseClaim = expenseClaimRepository
            .findById(expenseClaimId)
            .orElseThrow(() -> EntityNotFoundException.create("ExpenseClaim", expenseClaimId));

        return submitForApproval(
            new SubmitApprovalRequest(
                ApprovalType.EXPENSE_CLAIM,
                expenseClaimId,
                "ExpenseClaim",
                expenseClaim.getEmployee().getId(),
                expenseClaim.getAmount().doubleValue(),
                null,
                3,
                "Expense claim: " + expenseClaim.getDescription() + " - Amount: " + expenseClaim.getAmount()
            )
        );
    }

    // ==================== OVERTIME-SPECIFIC METHODS ====================

    /**
     * Submit an overtime request for approval.
     */
    public ApprovalWorkflowResponse submitOvertimeRequest(UUID overtimeRecordId) {
        OvertimeRecord overtimeRecord = overtimeRecordRepository
            .findById(overtimeRecordId)
            .orElseThrow(() -> EntityNotFoundException.create("OvertimeRecord", overtimeRecordId));

        return submitForApproval(
            new SubmitApprovalRequest(
                ApprovalType.OVERTIME_REQUEST,
                overtimeRecordId,
                "OvertimeRecord",
                overtimeRecord.getEmployee().getId(),
                overtimeRecord.getHours().doubleValue(),
                null,
                3,
                "Overtime request: " + overtimeRecord.getHours() + " hours on " + overtimeRecord.getDate()
            )
        );
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private void handleApproval(ApprovalRequest approvalRequest, WorkflowInstance workflow, String comments) {
        // Get approval chain to check if there are more levels
        List<ApprovalChainConfig> approvalChain = getApprovalChain(
            approvalRequest.getApprovalType(),
            approvalRequest.getAmount(),
            approvalRequest.getRequestor().getDepartment() != null ? approvalRequest.getRequestor().getDepartment().getId() : null
        );

        int currentLevel = approvalRequest.getCurrentLevel();
        int totalLevels = approvalChain.isEmpty() ? 1 : approvalChain.size();

        if (currentLevel < totalLevels) {
            // Move to next approval level
            ApprovalChainConfig nextLevelConfig = approvalChain.get(currentLevel);
            Employee nextApprover = determineApprover(nextLevelConfig, approvalRequest.getRequestor());

            if (nextApprover != null) {
                approvalRequest.moveToNextLevel(nextApprover);
                approvalRequest.setApproverComments(comments);
                approvalRequestRepository.save(approvalRequest);

                // Update workflow
                workflowStateManager.transitionState(
                    workflow.getId(),
                    "PENDING_LEVEL_" + (currentLevel + 1),
                    "Approved at level " + currentLevel + ", awaiting level " + (currentLevel + 1)
                );
                workflowStateManager.assignWorkflow(workflow.getId(), nextApprover.getId());

                // Notify next approver
                notificationService.notifyApprovalRequired(
                    nextApprover.getId(),
                    getApprovalNotificationTitle(approvalRequest.getApprovalType()),
                    "Approval request from " +
                    approvalRequest.getRequestor().getFirstName() +
                    " requires your approval (Level " +
                    (currentLevel + 1) +
                    ")",
                    approvalRequest.getEntityId(),
                    approvalRequest.getEntityType()
                );

                log.info("Moved approval {} to level {}", approvalRequest.getId(), currentLevel + 1);
                return;
            }
        }

        // Final approval
        approvalRequest.approve(comments);
        approvalRequestRepository.save(approvalRequest);

        // Update the actual entity status
        updateEntityStatus(approvalRequest, true);

        // Complete workflow
        workflowStateManager.completeWorkflow(workflow.getId(), "APPROVED");

        // Notify requestor
        notificationService.notifyApprovalDecision(
            approvalRequest.getRequestor().getId(),
            "Request Approved",
            "Your " + approvalRequest.getApprovalType().name().toLowerCase().replace("_", " ") + " has been approved.",
            approvalRequest.getEntityId(),
            approvalRequest.getEntityType(),
            true
        );

        log.info("Approved approval request {}", approvalRequest.getId());
    }

    private void handleRejection(ApprovalRequest approvalRequest, WorkflowInstance workflow, String comments) {
        approvalRequest.reject(comments);
        approvalRequestRepository.save(approvalRequest);

        // Update the actual entity status
        updateEntityStatus(approvalRequest, false);

        // Fail workflow
        workflowStateManager.completeWorkflow(workflow.getId(), "REJECTED");

        // Notify requestor
        notificationService.notifyApprovalDecision(
            approvalRequest.getRequestor().getId(),
            "Request Rejected",
            "Your " + approvalRequest.getApprovalType().name().toLowerCase().replace("_", " ") + " has been rejected. Reason: " + comments,
            approvalRequest.getEntityId(),
            approvalRequest.getEntityType(),
            false
        );

        log.info("Rejected approval request {}", approvalRequest.getId());
    }

    private void handleMoreInfoRequest(ApprovalRequest approvalRequest, String comments) {
        approvalRequest.setStatus(WorkflowStatus.ON_HOLD);
        approvalRequest.setApproverComments("More information requested: " + comments);
        approvalRequestRepository.save(approvalRequest);

        // Notify requestor
        notificationService.sendReminder(
            approvalRequest.getRequestor().getId(),
            "Additional Information Required",
            "Your approver has requested more information: " + comments,
            approvalRequest.getEntityId(),
            approvalRequest.getEntityType()
        );

        log.info("Requested more info for approval request {}", approvalRequest.getId());
    }

    private void updateEntityStatus(ApprovalRequest approvalRequest, boolean approved) {
        switch (approvalRequest.getApprovalType()) {
            case LEAVE_REQUEST -> {
                leaveRequestRepository
                    .findById(approvalRequest.getEntityId())
                    .ifPresent(lr -> {
                        lr.setStatus(approved ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
                        lr.setApprover(approvalRequest.getApprover());
                        lr.setApproverComments(approvalRequest.getApproverComments());
                        leaveRequestRepository.save(lr);
                    });
            }
            case EXPENSE_CLAIM -> {
                expenseClaimRepository
                    .findById(approvalRequest.getEntityId())
                    .ifPresent(ec -> {
                        ec.setStatus(approved ? ExpenseClaimStatus.APPROVED : ExpenseClaimStatus.REJECTED);
                        expenseClaimRepository.save(ec);
                    });
            }
            case OVERTIME_REQUEST -> {
                overtimeRecordRepository
                    .findById(approvalRequest.getEntityId())
                    .ifPresent(or -> {
                        or.setApprovalStatus(approved ? OvertimeApprovalStatus.APPROVED : OvertimeApprovalStatus.REJECTED);
                        or.setApprovedBy(approvalRequest.getApprover());
                        overtimeRecordRepository.save(or);
                    });
            }
            default -> log.warn("Unknown approval type: {}", approvalRequest.getApprovalType());
        }
    }

    private List<ApprovalChainConfig> getApprovalChain(ApprovalType type, Double amount, UUID departmentId) {
        List<ApprovalChainConfig> chain;

        if (amount != null) {
            chain = approvalChainConfigRepository.findByApprovalTypeAndAmountThreshold(type, amount);
        } else if (departmentId != null) {
            chain = approvalChainConfigRepository.findByApprovalTypeAndDepartmentIdAndActiveTrueOrderBySequenceOrderAsc(type, departmentId);
        } else {
            chain = approvalChainConfigRepository.findByApprovalTypeAndActiveTrueOrderBySequenceOrderAsc(type);
        }

        return chain;
    }

    private List<ApprovalChainConfig> createDefaultApprovalChain(ApprovalType type) {
        ApprovalChainConfig defaultConfig = new ApprovalChainConfig();
        defaultConfig.setApprovalType(type);
        defaultConfig.setSequenceOrder(1);
        defaultConfig.setApproverType(ApproverType.DIRECT_MANAGER);
        defaultConfig.setActive(true);
        return List.of(defaultConfig);
    }

    private Employee determineApprover(ApprovalChainConfig config, Employee requestor) {
        return switch (config.getApproverType()) {
            case DIRECT_MANAGER -> requestor.getManager();
            case DEPARTMENT_HEAD -> {
                if (requestor.getDepartment() != null && requestor.getDepartment().getHead() != null) {
                    yield requestor.getDepartment().getHead();
                }
                yield requestor.getManager();
            }
            case SPECIFIC_EMPLOYEE -> {
                if (config.getSpecificApproverId() != null) {
                    yield employeeRepository.findById(config.getSpecificApproverId()).orElse(null);
                }
                yield null;
            }
            case HR, FINANCE, EXECUTIVE -> {
                // Would need to look up employees with specific roles
                yield requestor.getManager();
            }
        };
    }

    private WorkflowType mapApprovalTypeToWorkflowType(ApprovalType approvalType) {
        return switch (approvalType) {
            case LEAVE_REQUEST -> WorkflowType.LEAVE_APPROVAL;
            case EXPENSE_CLAIM -> WorkflowType.EXPENSE_APPROVAL;
            case OVERTIME_REQUEST -> WorkflowType.OVERTIME_APPROVAL;
            case TRAINING_REQUEST -> WorkflowType.TRAINING_ENROLLMENT;
            case POSITION_TRANSFER -> WorkflowType.TRANSFER;
            case TIMESHEET_APPROVAL -> WorkflowType.TIMESHEET_APPROVAL;
            default -> WorkflowType.LEAVE_APPROVAL;
        };
    }

    private String getApprovalNotificationTitle(ApprovalType type) {
        return switch (type) {
            case LEAVE_REQUEST -> "Leave Request Pending Approval";
            case EXPENSE_CLAIM -> "Expense Claim Pending Approval";
            case OVERTIME_REQUEST -> "Overtime Request Pending Approval";
            case TRAINING_REQUEST -> "Training Request Pending Approval";
            case POSITION_TRANSFER -> "Transfer Request Pending Approval";
            case TIMESHEET_APPROVAL -> "Timesheet Pending Approval";
            default -> "Request Pending Approval";
        };
    }

    private String buildApprovalNotificationMessage(SubmitApprovalRequest request, Employee requestor) {
        StringBuilder message = new StringBuilder();
        message.append(requestor.getFirstName()).append(" ").append(requestor.getLastName());
        message.append(" has submitted a ").append(request.approvalType().name().toLowerCase().replace("_", " "));
        message.append(" for your approval.");

        if (request.amount() != null) {
            message.append(" Amount: $").append(String.format("%.2f", request.amount()));
        }
        if (request.daysCount() != null) {
            message.append(" Days: ").append(request.daysCount());
        }

        return message.toString();
    }

    private ApprovalWorkflowResponse mapToApprovalResponse(ApprovalRequest request) {
        List<ApprovalWorkflowResponse.ApprovalHistoryItem> history = new ArrayList<>();
        // In a real implementation, we would track approval history in a separate table
        if (request.getDecidedAt() != null) {
            history.add(
                new ApprovalWorkflowResponse.ApprovalHistoryItem(
                    request.getCurrentLevel(),
                    request.getApprover().getId(),
                    request.getApprover().getFirstName() + " " + request.getApprover().getLastName(),
                    request.getStatus().name(),
                    request.getApproverComments(),
                    request.getDecidedAt()
                )
            );
        }

        Integer totalLevels = 1;
        if (request.getWorkflowInstance() != null && request.getWorkflowInstance().getContext() != null) {
            Object levels = request.getWorkflowInstance().getContext().get("totalLevels");
            if (levels instanceof Integer) {
                totalLevels = (Integer) levels;
            }
        }

        return new ApprovalWorkflowResponse(
            request.getId(),
            request.getWorkflowInstance() != null ? request.getWorkflowInstance().getId() : null,
            request.getApprovalType(),
            request.getEntityId(),
            request.getEntityType(),
            request.getStatus(),
            request.getRequestor().getId(),
            request.getRequestor().getFirstName() + " " + request.getRequestor().getLastName(),
            request.getApprover().getId(),
            request.getApprover().getFirstName() + " " + request.getApprover().getLastName(),
            request.getCurrentLevel(),
            totalLevels,
            request.getAmount(),
            request.getDaysCount(),
            request.getPriority(),
            request.getSubmittedAt(),
            request.getDecidedAt(),
            request.getDueDate(),
            request.getApproverComments(),
            history,
            request.getCreatedDate(),
            request.getLastModifiedDate()
        );
    }

    private PendingApprovalSummary mapToPendingApprovalSummary(ApprovalRequest request) {
        long daysWaiting = Duration.between(request.getSubmittedAt(), Instant.now()).toDays();
        boolean isOverdue = request.getDueDate() != null && Instant.now().isAfter(request.getDueDate());

        String entityDescription =
            switch (request.getApprovalType()) {
                case LEAVE_REQUEST -> "Leave request";
                case EXPENSE_CLAIM -> "Expense claim" +
                (request.getAmount() != null ? " - $" + String.format("%.2f", request.getAmount()) : "");
                case OVERTIME_REQUEST -> "Overtime request" + (request.getAmount() != null ? " - " + request.getAmount() + " hours" : "");
                default -> request.getEntityType();
            };

        return new PendingApprovalSummary(
            request.getId(),
            request.getApprovalType(),
            request.getEntityId(),
            request.getEntityType(),
            entityDescription,
            request.getRequestor().getId(),
            request.getRequestor().getFirstName() + " " + request.getRequestor().getLastName(),
            request.getStatus(),
            request.getPriority(),
            request.getAmount(),
            request.getDaysCount(),
            request.getSubmittedAt(),
            request.getDueDate(),
            isOverdue,
            daysWaiting
        );
    }
}
