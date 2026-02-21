package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.*;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.requests.ApprovalDecisionRequest;
import com.humano.dto.hr.workflow.requests.InitiateTransferRequest;
import com.humano.dto.hr.workflow.responses.TransferWorkflowResponse;
import com.humano.repository.hr.*;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.workflow.infrastructure.DeadlineMonitorService;
import com.humano.service.hr.workflow.infrastructure.NotificationOrchestrationService;
import com.humano.service.hr.workflow.infrastructure.WorkflowStateManager;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing employee transfer workflows.
 * Handles position and department transfers with multi-party approvals.
 */
@Service
@Transactional
public class TransferWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(TransferWorkflowService.class);

    // Transfer states
    public static final String STATE_INITIATED = "INITIATED";
    public static final String STATE_PENDING_CURRENT_MANAGER = "PENDING_CURRENT_MANAGER_APPROVAL";
    public static final String STATE_PENDING_NEW_MANAGER = "PENDING_NEW_MANAGER_APPROVAL";
    public static final String STATE_PENDING_HR = "PENDING_HR_APPROVAL";
    public static final String STATE_APPROVED = "APPROVED";
    public static final String STATE_SCHEDULED = "SCHEDULED";
    public static final String STATE_COMPLETED = "COMPLETED";
    public static final String STATE_REJECTED = "REJECTED";

    private final WorkflowStateManager workflowStateManager;
    private final NotificationOrchestrationService notificationService;
    private final DeadlineMonitorService deadlineMonitorService;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final OrganizationalUnitRepository organizationalUnitRepository;
    private final EmployeePositionHistoryRepository positionHistoryRepository;

    public TransferWorkflowService(
        WorkflowStateManager workflowStateManager,
        NotificationOrchestrationService notificationService,
        DeadlineMonitorService deadlineMonitorService,
        EmployeeRepository employeeRepository,
        DepartmentRepository departmentRepository,
        PositionRepository positionRepository,
        OrganizationalUnitRepository organizationalUnitRepository,
        EmployeePositionHistoryRepository positionHistoryRepository
    ) {
        this.workflowStateManager = workflowStateManager;
        this.notificationService = notificationService;
        this.deadlineMonitorService = deadlineMonitorService;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.positionRepository = positionRepository;
        this.organizationalUnitRepository = organizationalUnitRepository;
        this.positionHistoryRepository = positionHistoryRepository;
    }

    /**
     * Initiate a transfer request.
     */
    public TransferWorkflowResponse initiateTransfer(InitiateTransferRequest request) {
        log.info("Initiating transfer for employee {}", request.employeeId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        // Validate at least one change is being made
        if (
            request.newDepartmentId() == null &&
            request.newPositionId() == null &&
            request.newManagerId() == null &&
            request.newOrganizationalUnitId() == null
        ) {
            throw new BadRequestAlertException("At least one transfer change must be specified", "transfer", "nochange");
        }

        // Create workflow context
        Map<String, Object> context = new HashMap<>();
        context.put("employeeId", request.employeeId().toString());
        context.put("effectiveDate", request.effectiveDate().toString());
        context.put("reason", request.reason());
        context.put("requiresRelocation", request.requiresRelocation());

        // Store current values
        if (employee.getDepartment() != null) {
            context.put("currentDepartmentId", employee.getDepartment().getId().toString());
            context.put("currentDepartmentName", employee.getDepartment().getName());
        }
        if (employee.getPosition() != null) {
            context.put("currentPositionId", employee.getPosition().getId().toString());
            context.put("currentPositionTitle", employee.getPosition().getName());
        }
        if (employee.getManager() != null) {
            context.put("currentManagerId", employee.getManager().getId().toString());
            context.put("currentManagerName", employee.getManager().getFirstName() + " " + employee.getManager().getLastName());
        }
        if (employee.getUnit() != null) {
            context.put("currentOrganizationalUnitId", employee.getUnit().getId().toString());
            context.put("currentOrganizationalUnitName", employee.getUnit().getName());
        }

        // Store new values
        if (request.newDepartmentId() != null) {
            Department newDept = departmentRepository
                .findById(request.newDepartmentId())
                .orElseThrow(() -> EntityNotFoundException.create("Department", request.newDepartmentId()));
            context.put("newDepartmentId", newDept.getId().toString());
            context.put("newDepartmentName", newDept.getName());
        }
        if (request.newPositionId() != null) {
            Position newPos = positionRepository
                .findById(request.newPositionId())
                .orElseThrow(() -> EntityNotFoundException.create("Position", request.newPositionId()));
            context.put("newPositionId", newPos.getId().toString());
            context.put("newPositionTitle", newPos.getName());
        }
        if (request.newManagerId() != null) {
            Employee newManager = employeeRepository
                .findById(request.newManagerId())
                .orElseThrow(() -> EntityNotFoundException.create("Employee", request.newManagerId()));
            context.put("newManagerId", newManager.getId().toString());
            context.put("newManagerName", newManager.getFirstName() + " " + newManager.getLastName());
        }
        if (request.newOrganizationalUnitId() != null) {
            OrganizationalUnit newUnit = organizationalUnitRepository
                .findById(request.newOrganizationalUnitId())
                .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", request.newOrganizationalUnitId()));
            context.put("newOrganizationalUnitId", newUnit.getId().toString());
            context.put("newOrganizationalUnitName", newUnit.getName());
        }

        // Create workflow
        WorkflowInstance workflow = workflowStateManager.createWorkflow(
            WorkflowType.TRANSFER,
            request.employeeId(),
            "Employee",
            context,
            null
        );

        // Start workflow and set initial state
        workflowStateManager.startWorkflow(workflow.getId());
        workflowStateManager.transitionState(
            workflow.getId(),
            STATE_PENDING_CURRENT_MANAGER,
            "Transfer request initiated - awaiting current manager approval"
        );

        // Set due date
        Instant dueDate = request.effectiveDate().minusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant();
        workflowStateManager.updateDueDate(workflow.getId(), dueDate);

        // Register deadline
        deadlineMonitorService.registerDeadline(
            workflow.getId(),
            "TRANSFER_APPROVAL",
            "Transfer approval required for " + employee.getFirstName() + " " + employee.getLastName(),
            dueDate,
            72,
            employee.getManager() != null ? employee.getManager().getId() : null
        );

        // Notify current manager
        if (employee.getManager() != null) {
            notificationService.notifyApprovalRequired(
                employee.getManager().getId(),
                "Transfer Request Approval Required",
                employee.getFirstName() + " " + employee.getLastName() + " has a pending transfer request. Reason: " + request.reason(),
                workflow.getId(),
                "Transfer"
            );
        }

        log.info("Transfer workflow {} initiated for employee {}", workflow.getId(), request.employeeId());

        return getTransferStatus(workflow.getId());
    }

    /**
     * Get transfer workflow status.
     */
    @Transactional(readOnly = true)
    public TransferWorkflowResponse getTransferStatus(UUID workflowId) {
        WorkflowInstance workflow = workflowStateManager.getWorkflow(workflowId);

        if (workflow.getWorkflowType() != WorkflowType.TRANSFER) {
            throw new BadRequestAlertException("Workflow is not a transfer workflow", "workflow", "invalidtype");
        }

        return mapToTransferResponse(workflow);
    }

    /**
     * Process current manager approval.
     */
    public TransferWorkflowResponse processCurrentManagerApproval(UUID workflowId, ApprovalDecisionRequest decision) {
        log.info("Processing current manager approval for transfer {}: {}", workflowId, decision.decision());

        WorkflowInstance workflow = workflowStateManager.getWorkflow(workflowId);

        if (!STATE_PENDING_CURRENT_MANAGER.equals(workflow.getCurrentState())) {
            throw new BadRequestAlertException("Transfer is not pending current manager approval", "transfer", "invalidstate");
        }

        Map<String, Object> context = workflow.getContext();

        if (decision.decision() == ApprovalDecisionRequest.ApprovalDecision.APPROVE) {
            context.put("currentManagerApproved", true);
            context.put("currentManagerComments", decision.comments());
            context.put("currentManagerDecisionAt", Instant.now().toString());
            workflowStateManager.updateContext(workflowId, "currentManagerApproved", true);
            workflowStateManager.updateContext(workflowId, "currentManagerComments", decision.comments());

            // Move to next state
            String newManagerId = (String) context.get("newManagerId");
            if (newManagerId != null) {
                workflowStateManager.transitionState(
                    workflowId,
                    STATE_PENDING_NEW_MANAGER,
                    "Current manager approved - awaiting new manager approval"
                );

                // Notify new manager
                notificationService.notifyApprovalRequired(
                    UUID.fromString(newManagerId),
                    "Transfer Request Approval Required",
                    "An employee transfer to your team requires your approval.",
                    workflowId,
                    "Transfer"
                );
            } else {
                // No new manager, go to HR
                workflowStateManager.transitionState(workflowId, STATE_PENDING_HR, "Current manager approved - awaiting HR approval");
            }
        } else {
            // Rejected
            context.put("currentManagerApproved", false);
            context.put("currentManagerComments", decision.comments());
            workflowStateManager.updateContext(workflowId, "currentManagerApproved", false);
            workflowStateManager.transitionState(
                workflowId,
                STATE_REJECTED,
                "Transfer rejected by current manager: " + decision.comments()
            );
            workflowStateManager.updateStatus(workflowId, WorkflowStatus.REJECTED, decision.comments());

            // Notify employee
            String employeeId = (String) context.get("employeeId");
            notificationService.notifyApprovalDecision(
                UUID.fromString(employeeId),
                "Transfer Request Rejected",
                "Your transfer request has been rejected by your current manager. Reason: " + decision.comments(),
                workflowId,
                "Transfer",
                false
            );
        }

        return getTransferStatus(workflowId);
    }

    /**
     * Process new manager approval.
     */
    public TransferWorkflowResponse processNewManagerApproval(UUID workflowId, ApprovalDecisionRequest decision) {
        log.info("Processing new manager approval for transfer {}: {}", workflowId, decision.decision());

        WorkflowInstance workflow = workflowStateManager.getWorkflow(workflowId);

        if (!STATE_PENDING_NEW_MANAGER.equals(workflow.getCurrentState())) {
            throw new BadRequestAlertException("Transfer is not pending new manager approval", "transfer", "invalidstate");
        }

        Map<String, Object> context = workflow.getContext();

        if (decision.decision() == ApprovalDecisionRequest.ApprovalDecision.APPROVE) {
            workflowStateManager.updateContext(workflowId, "newManagerApproved", true);
            workflowStateManager.updateContext(workflowId, "newManagerComments", decision.comments());

            // Move to HR approval
            workflowStateManager.transitionState(workflowId, STATE_PENDING_HR, "New manager approved - awaiting HR approval");
        } else {
            // Rejected
            workflowStateManager.updateContext(workflowId, "newManagerApproved", false);
            workflowStateManager.updateContext(workflowId, "newManagerComments", decision.comments());
            workflowStateManager.transitionState(workflowId, STATE_REJECTED, "Transfer rejected by new manager: " + decision.comments());
            workflowStateManager.updateStatus(workflowId, WorkflowStatus.REJECTED, decision.comments());

            // Notify employee
            String employeeId = (String) context.get("employeeId");
            notificationService.notifyApprovalDecision(
                UUID.fromString(employeeId),
                "Transfer Request Rejected",
                "Your transfer request has been rejected by the receiving manager. Reason: " + decision.comments(),
                workflowId,
                "Transfer",
                false
            );
        }

        return getTransferStatus(workflowId);
    }

    /**
     * Process HR approval.
     */
    public TransferWorkflowResponse processHRApproval(UUID workflowId, ApprovalDecisionRequest decision) {
        log.info("Processing HR approval for transfer {}: {}", workflowId, decision.decision());

        WorkflowInstance workflow = workflowStateManager.getWorkflow(workflowId);

        if (!STATE_PENDING_HR.equals(workflow.getCurrentState())) {
            throw new BadRequestAlertException("Transfer is not pending HR approval", "transfer", "invalidstate");
        }

        Map<String, Object> context = workflow.getContext();

        if (decision.decision() == ApprovalDecisionRequest.ApprovalDecision.APPROVE) {
            workflowStateManager.updateContext(workflowId, "hrApproved", true);
            workflowStateManager.updateContext(workflowId, "hrComments", decision.comments());

            // Move to approved/scheduled state
            workflowStateManager.transitionState(workflowId, STATE_APPROVED, "Transfer approved by HR - scheduled for effective date");
            workflowStateManager.updateStatus(workflowId, WorkflowStatus.APPROVED, "All approvals received");

            // Notify employee
            String employeeId = (String) context.get("employeeId");
            String effectiveDate = (String) context.get("effectiveDate");
            notificationService.notifyApprovalDecision(
                UUID.fromString(employeeId),
                "Transfer Request Approved",
                "Your transfer request has been approved! Effective date: " + effectiveDate,
                workflowId,
                "Transfer",
                true
            );
        } else {
            // Rejected
            workflowStateManager.updateContext(workflowId, "hrApproved", false);
            workflowStateManager.updateContext(workflowId, "hrComments", decision.comments());
            workflowStateManager.transitionState(workflowId, STATE_REJECTED, "Transfer rejected by HR: " + decision.comments());
            workflowStateManager.updateStatus(workflowId, WorkflowStatus.REJECTED, decision.comments());

            // Notify employee
            String employeeId = (String) context.get("employeeId");
            notificationService.notifyApprovalDecision(
                UUID.fromString(employeeId),
                "Transfer Request Rejected",
                "Your transfer request has been rejected by HR. Reason: " + decision.comments(),
                workflowId,
                "Transfer",
                false
            );
        }

        return getTransferStatus(workflowId);
    }

    /**
     * Execute an approved transfer.
     */
    public TransferWorkflowResponse executeTransfer(UUID workflowId) {
        log.info("Executing transfer {}", workflowId);

        WorkflowInstance workflow = workflowStateManager.getWorkflow(workflowId);

        if (!STATE_APPROVED.equals(workflow.getCurrentState()) && !STATE_SCHEDULED.equals(workflow.getCurrentState())) {
            throw new BadRequestAlertException("Transfer must be approved before execution", "transfer", "notapproved");
        }

        Map<String, Object> context = workflow.getContext();
        UUID employeeId = UUID.fromString((String) context.get("employeeId"));

        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));

        // Record position history before making changes
        recordPositionHistory(employee, context);

        // Apply the transfer changes
        if (context.containsKey("newDepartmentId")) {
            UUID newDeptId = UUID.fromString((String) context.get("newDepartmentId"));
            Department newDept = departmentRepository
                .findById(newDeptId)
                .orElseThrow(() -> EntityNotFoundException.create("Department", newDeptId));
            employee.setDepartment(newDept);
        }

        if (context.containsKey("newPositionId")) {
            UUID newPosId = UUID.fromString((String) context.get("newPositionId"));
            Position newPos = positionRepository.findById(newPosId).orElseThrow(() -> EntityNotFoundException.create("Position", newPosId));
            employee.setPosition(newPos);
        }

        if (context.containsKey("newManagerId")) {
            UUID newMgrId = UUID.fromString((String) context.get("newManagerId"));
            Employee newManager = employeeRepository
                .findById(newMgrId)
                .orElseThrow(() -> EntityNotFoundException.create("Employee", newMgrId));
            employee.setManager(newManager);
        }

        if (context.containsKey("newOrganizationalUnitId")) {
            UUID newUnitId = UUID.fromString((String) context.get("newOrganizationalUnitId"));
            OrganizationalUnit newUnit = organizationalUnitRepository
                .findById(newUnitId)
                .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", newUnitId));
            employee.setUnit(newUnit);
        }

        // Note: OrganizationalUnit changes are tracked in history but not directly set on Employee
        // as the Employee entity may not support this field directly

        employeeRepository.save(employee);

        // Complete workflow
        workflowStateManager.transitionState(workflowId, STATE_COMPLETED, "Transfer executed successfully");
        workflowStateManager.completeWorkflow(workflowId, "Transfer completed");

        // Notify employee
        notificationService.notifyWorkflowCompleted(
            employeeId,
            "Transfer Completed",
            "Your transfer has been completed. Welcome to your new role!",
            workflowId,
            "Transfer"
        );

        log.info("Transfer {} executed successfully for employee {}", workflowId, employeeId);

        return getTransferStatus(workflowId);
    }

    /**
     * Cancel a transfer request.
     */
    public void cancelTransfer(UUID workflowId, String reason) {
        log.info("Cancelling transfer {}: {}", workflowId, reason);

        WorkflowInstance workflow = workflowStateManager.getWorkflow(workflowId);

        if (STATE_COMPLETED.equals(workflow.getCurrentState())) {
            throw new BadRequestAlertException("Cannot cancel a completed transfer", "transfer", "alreadycompleted");
        }

        workflowStateManager.cancelWorkflow(workflowId, reason);

        Map<String, Object> context = workflow.getContext();
        String employeeId = (String) context.get("employeeId");

        notificationService.notifyWorkflowCompleted(
            UUID.fromString(employeeId),
            "Transfer Cancelled",
            "Your transfer request has been cancelled. Reason: " + reason,
            workflowId,
            "Transfer"
        );

        log.info("Transfer {} cancelled", workflowId);
    }

    /**
     * Get employee's position history.
     */
    @Transactional(readOnly = true)
    public Page<EmployeePositionHistory> getEmployeePositionHistory(UUID employeeId, Pageable pageable) {
        return positionHistoryRepository.findByEmployeeIdOrderByEffectiveDateDesc(employeeId, pageable);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private void recordPositionHistory(Employee employee, Map<String, Object> context) {
        EmployeePositionHistory history = new EmployeePositionHistory();
        history.setEmployee(employee);
        history.setOldPosition(employee.getPosition());
        history.setOldManager(employee.getManager());
        history.setEffectiveDate(LocalDate.now());
        history.setReason((String) context.get("reason"));

        positionHistoryRepository.save(history);
    }

    private TransferWorkflowResponse mapToTransferResponse(WorkflowInstance workflow) {
        Map<String, Object> context = workflow.getContext();

        UUID employeeId = UUID.fromString((String) context.get("employeeId"));
        Employee employee = employeeRepository.findById(employeeId).orElse(null);

        TransferWorkflowResponse.TransferDetails currentDetails = new TransferWorkflowResponse.TransferDetails(
            context.containsKey("currentDepartmentId") ? UUID.fromString((String) context.get("currentDepartmentId")) : null,
            (String) context.get("currentDepartmentName"),
            context.containsKey("currentPositionId") ? UUID.fromString((String) context.get("currentPositionId")) : null,
            (String) context.get("currentPositionTitle"),
            context.containsKey("currentManagerId") ? UUID.fromString((String) context.get("currentManagerId")) : null,
            (String) context.get("currentManagerName"),
            context.containsKey("currentOrganizationalUnitId")
                ? UUID.fromString((String) context.get("currentOrganizationalUnitId"))
                : null,
            (String) context.get("currentOrganizationalUnitName")
        );

        TransferWorkflowResponse.TransferDetails newDetails = new TransferWorkflowResponse.TransferDetails(
            context.containsKey("newDepartmentId") ? UUID.fromString((String) context.get("newDepartmentId")) : null,
            (String) context.get("newDepartmentName"),
            context.containsKey("newPositionId") ? UUID.fromString((String) context.get("newPositionId")) : null,
            (String) context.get("newPositionTitle"),
            context.containsKey("newManagerId") ? UUID.fromString((String) context.get("newManagerId")) : null,
            (String) context.get("newManagerName"),
            context.containsKey("newOrganizationalUnitId") ? UUID.fromString((String) context.get("newOrganizationalUnitId")) : null,
            (String) context.get("newOrganizationalUnitName")
        );

        TransferWorkflowResponse.ApprovalStatus approvalStatus = new TransferWorkflowResponse.ApprovalStatus(
            Boolean.TRUE.equals(context.get("currentManagerApproved")),
            (String) context.get("currentManagerComments"),
            context.containsKey("currentManagerDecisionAt") ? Instant.parse((String) context.get("currentManagerDecisionAt")) : null,
            Boolean.TRUE.equals(context.get("newManagerApproved")),
            (String) context.get("newManagerComments"),
            context.containsKey("newManagerDecisionAt") ? Instant.parse((String) context.get("newManagerDecisionAt")) : null,
            Boolean.TRUE.equals(context.get("hrApproved")),
            (String) context.get("hrComments"),
            context.containsKey("hrDecisionAt") ? Instant.parse((String) context.get("hrDecisionAt")) : null
        );

        LocalDate effectiveDate = context.containsKey("effectiveDate") ? LocalDate.parse((String) context.get("effectiveDate")) : null;

        return new TransferWorkflowResponse(
            workflow.getId(),
            workflow.getId(),
            employeeId,
            employee != null ? employee.getFirstName() + " " + employee.getLastName() : null,
            workflow.getStatus(),
            workflow.getCurrentState(),
            currentDetails,
            newDetails,
            effectiveDate,
            (String) context.get("reason"),
            approvalStatus,
            workflow.getCreatedDate(),
            workflow.getLastModifiedDate()
        );
    }
}
