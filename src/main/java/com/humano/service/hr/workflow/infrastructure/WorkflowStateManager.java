package com.humano.service.hr.workflow.infrastructure;

import com.humano.domain.enumeration.hr.WorkflowStatus;
import com.humano.domain.enumeration.hr.WorkflowType;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.hr.WorkflowStateTransition;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.workflow.responses.WorkflowResponse;
import com.humano.repository.hr.workflow.WorkflowInstanceRepository;
import com.humano.repository.hr.workflow.WorkflowStateTransitionRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.web.rest.errors.BadRequestAlertException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing workflow state and transitions.
 * Provides centralized workflow state persistence and management.
 */
@Service
@Transactional
public class WorkflowStateManager {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStateManager.class);

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowStateTransitionRepository transitionRepository;
    private final EmployeeRepository employeeRepository;

    public WorkflowStateManager(
        WorkflowInstanceRepository workflowInstanceRepository,
        WorkflowStateTransitionRepository transitionRepository,
        EmployeeRepository employeeRepository
    ) {
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.transitionRepository = transitionRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create a new workflow instance.
     */
    public WorkflowInstance createWorkflow(
        WorkflowType type,
        UUID entityId,
        String entityType,
        Map<String, Object> context,
        UUID initiatorId
    ) {
        log.debug("Creating workflow of type {} for entity {}", type, entityId);

        // Check if an active workflow already exists for this entity
        if (workflowInstanceRepository.existsActiveWorkflow(entityId, type)) {
            throw new BadRequestAlertException("An active workflow already exists for this entity", "workflow", "workflowexists");
        }

        WorkflowInstance workflow = new WorkflowInstance();
        workflow.setWorkflowType(type);
        workflow.setEntityId(entityId);
        workflow.setEntityType(entityType);
        workflow.setStatus(WorkflowStatus.DRAFT);
        workflow.setCurrentState("INITIATED");
        workflow.setContext(context != null ? context : Map.of());
        workflow.setPriority(3);

        if (initiatorId != null) {
            Employee initiator = employeeRepository
                .findById(initiatorId)
                .orElseThrow(() -> EntityNotFoundException.create("Employee", initiatorId));
            workflow.setInitiator(initiator);
        }

        WorkflowInstance saved = workflowInstanceRepository.save(workflow);
        log.info("Created workflow {} of type {} for entity {}", saved.getId(), type, entityId);

        // Add initial transition
        addTransition(saved.getId(), null, "INITIATED", "Workflow initiated", null);

        return saved;
    }

    /**
     * Start a workflow.
     */
    public WorkflowInstance startWorkflow(UUID workflowId) {
        log.debug("Starting workflow {}", workflowId);

        WorkflowInstance workflow = getWorkflow(workflowId);

        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new BadRequestAlertException("Workflow can only be started from DRAFT status", "workflow", "invalidstatus");
        }

        String previousState = workflow.getCurrentState();
        workflow.start();
        workflow.setCurrentState("IN_PROGRESS");

        WorkflowInstance saved = workflowInstanceRepository.save(workflow);
        addTransition(workflowId, previousState, "IN_PROGRESS", "Workflow started", getCurrentUser());

        log.info("Started workflow {}", workflowId);
        return saved;
    }

    /**
     * Transition workflow to a new state.
     */
    public WorkflowInstance transitionState(UUID workflowId, String targetState, String reason) {
        log.debug("Transitioning workflow {} to state {}", workflowId, targetState);

        WorkflowInstance workflow = getWorkflow(workflowId);
        String previousState = workflow.getCurrentState();

        workflow.setCurrentState(targetState);
        WorkflowInstance saved = workflowInstanceRepository.save(workflow);

        addTransition(workflowId, previousState, targetState, reason, getCurrentUser());

        log.info("Transitioned workflow {} from {} to {}", workflowId, previousState, targetState);
        return saved;
    }

    /**
     * Update workflow status.
     */
    public WorkflowInstance updateStatus(UUID workflowId, WorkflowStatus status, String reason) {
        log.debug("Updating workflow {} status to {}", workflowId, status);

        WorkflowInstance workflow = getWorkflow(workflowId);
        WorkflowStatus previousStatus = workflow.getStatus();

        workflow.setStatus(status);

        if (status == WorkflowStatus.COMPLETED || status == WorkflowStatus.CANCELLED || status == WorkflowStatus.FAILED) {
            workflow.setCompletedAt(Instant.now());
        }

        WorkflowInstance saved = workflowInstanceRepository.save(workflow);

        addTransition(workflowId, previousStatus.name(), status.name(), reason, getCurrentUser());

        log.info("Updated workflow {} status from {} to {}", workflowId, previousStatus, status);
        return saved;
    }

    /**
     * Complete a workflow successfully.
     */
    public WorkflowInstance completeWorkflow(UUID workflowId, String outcome) {
        log.debug("Completing workflow {} with outcome {}", workflowId, outcome);

        WorkflowInstance workflow = getWorkflow(workflowId);
        String previousState = workflow.getCurrentState();

        workflow.complete(outcome);
        workflow.setCurrentState("COMPLETED");

        WorkflowInstance saved = workflowInstanceRepository.save(workflow);
        addTransition(workflowId, previousState, "COMPLETED", "Workflow completed: " + outcome, getCurrentUser());

        log.info("Completed workflow {} with outcome {}", workflowId, outcome);
        return saved;
    }

    /**
     * Fail a workflow with an error.
     */
    public WorkflowInstance failWorkflow(UUID workflowId, String errorMessage) {
        log.debug("Failing workflow {} with error: {}", workflowId, errorMessage);

        WorkflowInstance workflow = getWorkflow(workflowId);
        String previousState = workflow.getCurrentState();

        workflow.fail(errorMessage);
        workflow.setCurrentState("FAILED");

        WorkflowInstance saved = workflowInstanceRepository.save(workflow);
        addTransition(workflowId, previousState, "FAILED", errorMessage, getCurrentUser());

        log.error("Failed workflow {} with error: {}", workflowId, errorMessage);
        return saved;
    }

    /**
     * Cancel a workflow.
     */
    public WorkflowInstance cancelWorkflow(UUID workflowId, String reason) {
        log.debug("Cancelling workflow {} with reason: {}", workflowId, reason);

        WorkflowInstance workflow = getWorkflow(workflowId);

        if (workflow.getStatus() == WorkflowStatus.COMPLETED || workflow.getStatus() == WorkflowStatus.CANCELLED) {
            throw new BadRequestAlertException("Cannot cancel a completed or already cancelled workflow", "workflow", "cannotcancel");
        }

        String previousState = workflow.getCurrentState();
        workflow.cancel(reason);
        workflow.setCurrentState("CANCELLED");

        WorkflowInstance saved = workflowInstanceRepository.save(workflow);
        addTransition(workflowId, previousState, "CANCELLED", reason, getCurrentUser());

        log.info("Cancelled workflow {} with reason: {}", workflowId, reason);
        return saved;
    }

    /**
     * Assign workflow to an employee.
     */
    public WorkflowInstance assignWorkflow(UUID workflowId, UUID assigneeId) {
        log.debug("Assigning workflow {} to employee {}", workflowId, assigneeId);

        WorkflowInstance workflow = getWorkflow(workflowId);
        Employee assignee = employeeRepository
            .findById(assigneeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", assigneeId));

        workflow.setCurrentAssignee(assignee);
        return workflowInstanceRepository.save(workflow);
    }

    /**
     * Update workflow context.
     */
    public WorkflowInstance updateContext(UUID workflowId, String key, Object value) {
        WorkflowInstance workflow = getWorkflow(workflowId);
        workflow.addContext(key, value);
        return workflowInstanceRepository.save(workflow);
    }

    /**
     * Update workflow due date.
     */
    public WorkflowInstance updateDueDate(UUID workflowId, Instant dueDate) {
        WorkflowInstance workflow = getWorkflow(workflowId);
        workflow.setDueDate(dueDate);
        return workflowInstanceRepository.save(workflow);
    }

    /**
     * Get workflow by ID.
     */
    @Transactional(readOnly = true)
    public WorkflowInstance getWorkflow(UUID workflowId) {
        return workflowInstanceRepository
            .findById(workflowId)
            .orElseThrow(() -> EntityNotFoundException.create("WorkflowInstance", workflowId));
    }

    /**
     * Get workflow response by ID.
     */
    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflowResponse(UUID workflowId) {
        WorkflowInstance workflow = getWorkflow(workflowId);
        return mapToResponse(workflow);
    }

    /**
     * Find workflows by entity ID.
     */
    @Transactional(readOnly = true)
    public List<WorkflowInstance> findByEntityId(UUID entityId) {
        return workflowInstanceRepository.findByEntityId(entityId);
    }

    /**
     * Find active workflow for entity.
     */
    @Transactional(readOnly = true)
    public List<WorkflowInstance> findActiveWorkflowsByEntityId(UUID entityId) {
        return workflowInstanceRepository.findActiveWorkflowsByEntityId(entityId);
    }

    /**
     * Find workflows by type and status.
     */
    @Transactional(readOnly = true)
    public Page<WorkflowInstance> findByTypeAndStatus(WorkflowType type, WorkflowStatus status, Pageable pageable) {
        return workflowInstanceRepository.findByWorkflowTypeAndStatus(type, status, pageable);
    }

    /**
     * Find workflows assigned to an employee.
     */
    @Transactional(readOnly = true)
    public Page<WorkflowInstance> findByAssignee(UUID assigneeId, Pageable pageable) {
        return workflowInstanceRepository.findByCurrentAssigneeId(assigneeId, pageable);
    }

    /**
     * Find overdue workflows.
     */
    @Transactional(readOnly = true)
    public List<WorkflowInstance> findOverdueWorkflows() {
        return workflowInstanceRepository.findOverdueWorkflows(Instant.now());
    }

    /**
     * Check if workflow is active.
     */
    @Transactional(readOnly = true)
    public boolean isWorkflowActive(UUID workflowId) {
        WorkflowInstance workflow = getWorkflow(workflowId);
        return (
            workflow.getStatus() != WorkflowStatus.COMPLETED &&
            workflow.getStatus() != WorkflowStatus.CANCELLED &&
            workflow.getStatus() != WorkflowStatus.FAILED
        );
    }

    /**
     * Get workflow history.
     */
    @Transactional(readOnly = true)
    public List<WorkflowStateTransition> getWorkflowHistory(UUID workflowId) {
        return transitionRepository.findByWorkflowInstanceIdOrderByTransitionedAtAsc(workflowId);
    }

    /**
     * Add a state transition record.
     */
    private WorkflowStateTransition addTransition(UUID workflowId, String fromState, String toState, String reason, String transitionedBy) {
        WorkflowInstance workflow = workflowInstanceRepository.getReferenceById(workflowId);

        WorkflowStateTransition transition = new WorkflowStateTransition();
        transition.setWorkflowInstance(workflow);
        transition.setFromState(fromState);
        transition.setToState(toState);
        transition.setReason(reason);
        transition.setTransitionedBy(transitionedBy);
        transition.setTransitionedAt(Instant.now());

        return transitionRepository.save(transition);
    }

    /**
     * Get current user from security context.
     */
    private String getCurrentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    /**
     * Map workflow instance to response DTO.
     */
    private WorkflowResponse mapToResponse(WorkflowInstance workflow) {
        List<WorkflowResponse.StateTransitionResponse> transitions = workflow
            .getTransitions()
            .stream()
            .map(t ->
                new WorkflowResponse.StateTransitionResponse(
                    t.getId(),
                    t.getFromState(),
                    t.getToState(),
                    t.getReason(),
                    t.getTransitionedBy(),
                    t.getTransitionedAt()
                )
            )
            .collect(Collectors.toList());

        String assigneeName = workflow.getCurrentAssignee() != null
            ? workflow.getCurrentAssignee().getFirstName() + " " + workflow.getCurrentAssignee().getLastName()
            : null;

        String initiatorName = workflow.getInitiator() != null
            ? workflow.getInitiator().getFirstName() + " " + workflow.getInitiator().getLastName()
            : null;

        int completionPercentage = calculateCompletionPercentage(workflow);

        return new WorkflowResponse(
            workflow.getId(),
            workflow.getWorkflowType(),
            workflow.getEntityId(),
            workflow.getEntityType(),
            workflow.getStatus(),
            workflow.getCurrentState(),
            workflow.getCurrentAssignee() != null ? workflow.getCurrentAssignee().getId() : null,
            assigneeName,
            workflow.getInitiator() != null ? workflow.getInitiator().getId() : null,
            initiatorName,
            workflow.getStartedAt(),
            workflow.getCompletedAt(),
            workflow.getDueDate(),
            transitions,
            workflow.getCreatedDate(),
            workflow.getLastModifiedDate()
        );
    }

    /**
     * Calculate workflow completion percentage based on status.
     */
    private int calculateCompletionPercentage(WorkflowInstance workflow) {
        return switch (workflow.getStatus()) {
            case DRAFT -> 0;
            case IN_PROGRESS -> 50;
            case PENDING_APPROVAL -> 60;
            case APPROVED -> 80;
            case COMPLETED -> 100;
            case CANCELLED, FAILED, REJECTED -> 0;
            default -> 0;
        };
    }
}
