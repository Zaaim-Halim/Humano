package com.humano.service.hr.workflow.infrastructure;

import com.humano.config.multitenancy.TenantContext;
import com.humano.domain.hr.WorkflowDeadline;
import com.humano.domain.hr.WorkflowInstance;
import com.humano.domain.shared.Employee;
import com.humano.events.EscalationTriggeredEvent;
import com.humano.repository.hr.workflow.WorkflowDeadlineRepository;
import com.humano.repository.hr.workflow.WorkflowInstanceRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for monitoring workflow deadlines and triggering escalations.
 * Runs scheduled tasks to check for approaching and overdue deadlines.
 */
@Service
@Transactional
public class DeadlineMonitorService {

    private static final Logger log = LoggerFactory.getLogger(DeadlineMonitorService.class);

    private final WorkflowDeadlineRepository deadlineRepository;
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationOrchestrationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Hard ceiling on auto-escalations per deadline. Prevents the hourly scheduler
     * from looping forever on a stuck workflow. Past the cap we keep logging but stop
     * mutating state and publishing events.
     */
    private final int maxEscalationLevel;

    public DeadlineMonitorService(
        WorkflowDeadlineRepository deadlineRepository,
        WorkflowInstanceRepository workflowInstanceRepository,
        EmployeeRepository employeeRepository,
        NotificationOrchestrationService notificationService,
        ApplicationEventPublisher eventPublisher,
        @Value("${humano.workflow.max-escalation-level:3}") int maxEscalationLevel
    ) {
        this.deadlineRepository = deadlineRepository;
        this.workflowInstanceRepository = workflowInstanceRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.maxEscalationLevel = maxEscalationLevel;
    }

    /**
     * Register a deadline for a workflow.
     */
    public WorkflowDeadline registerDeadline(
        UUID workflowId,
        String deadlineType,
        String description,
        Instant deadlineAt,
        Integer warningHoursBefore,
        UUID assigneeId
    ) {
        log.debug("Registering deadline for workflow {} - type: {}, deadline: {}", workflowId, deadlineType, deadlineAt);

        WorkflowInstance workflow = workflowInstanceRepository
            .findById(workflowId)
            .orElseThrow(() -> EntityNotFoundException.create("WorkflowInstance", workflowId));

        WorkflowDeadline deadline = new WorkflowDeadline();
        deadline.setWorkflowInstance(workflow);
        deadline.setDeadlineType(deadlineType);
        deadline.setDescription(description);
        deadline.setDeadlineAt(deadlineAt);

        if (warningHoursBefore != null && warningHoursBefore > 0) {
            deadline.setWarningAt(deadlineAt.minus(warningHoursBefore, ChronoUnit.HOURS));
        }

        if (assigneeId != null) {
            Employee assignee = employeeRepository
                .findById(assigneeId)
                .orElseThrow(() -> EntityNotFoundException.create("Employee", assigneeId));
            deadline.setAssignee(assignee);
        }

        WorkflowDeadline saved = deadlineRepository.save(deadline);
        log.info("Registered deadline {} for workflow {}", saved.getId(), workflowId);

        return saved;
    }

    /**
     * Update a deadline.
     */
    public WorkflowDeadline updateDeadline(UUID deadlineId, Instant newDeadlineAt) {
        log.debug("Updating deadline {} to {}", deadlineId, newDeadlineAt);

        WorkflowDeadline deadline = deadlineRepository
            .findById(deadlineId)
            .orElseThrow(() -> EntityNotFoundException.create("WorkflowDeadline", deadlineId));

        deadline.setDeadlineAt(newDeadlineAt);
        deadline.setWarningSent(false);
        deadline.setOverdueSent(false);

        return deadlineRepository.save(deadline);
    }

    /**
     * Complete a deadline.
     */
    public void completeDeadline(UUID deadlineId) {
        log.debug("Completing deadline {}", deadlineId);

        WorkflowDeadline deadline = deadlineRepository
            .findById(deadlineId)
            .orElseThrow(() -> EntityNotFoundException.create("WorkflowDeadline", deadlineId));

        deadline.complete();
        deadlineRepository.save(deadline);

        log.info("Completed deadline {}", deadlineId);
    }

    /**
     * Cancel a deadline.
     */
    public void cancelDeadline(UUID deadlineId) {
        log.debug("Cancelling deadline {}", deadlineId);
        deadlineRepository.deleteById(deadlineId);
        log.info("Cancelled deadline {}", deadlineId);
    }

    /**
     * Get deadlines for a workflow.
     */
    @Transactional(readOnly = true)
    public List<WorkflowDeadline> getDeadlinesByWorkflow(UUID workflowId) {
        return deadlineRepository.findByWorkflowInstanceId(workflowId);
    }

    /**
     * Get incomplete deadlines for a workflow.
     */
    @Transactional(readOnly = true)
    public List<WorkflowDeadline> getIncompleteDeadlines(UUID workflowId) {
        return deadlineRepository.findByWorkflowInstanceIdAndCompletedFalse(workflowId);
    }

    /**
     * Get deadlines assigned to an employee.
     */
    @Transactional(readOnly = true)
    public List<WorkflowDeadline> getDeadlinesByAssignee(UUID assigneeId) {
        return deadlineRepository.findByAssigneeIdAndCompletedFalse(assigneeId);
    }

    /**
     * Scheduled task to check for approaching deadlines (runs every hour).
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void checkApproachingDeadlines() {
        log.debug("Checking for approaching deadlines");

        Instant now = Instant.now();
        List<WorkflowDeadline> deadlinesNeedingWarning = deadlineRepository.findDeadlinesNeedingWarning(now);

        for (WorkflowDeadline deadline : deadlinesNeedingWarning) {
            try {
                sendWarningNotification(deadline);
                deadline.setWarningSent(true);
                deadlineRepository.save(deadline);
            } catch (Exception e) {
                log.error("Failed to send warning for deadline {}: {}", deadline.getId(), e.getMessage());
            }
        }

        log.info("Processed {} approaching deadline warnings", deadlinesNeedingWarning.size());
    }

    /**
     * Scheduled task to check for overdue items (runs every hour).
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void checkOverdueItems() {
        log.debug("Checking for overdue items");

        Instant now = Instant.now();
        List<WorkflowDeadline> overdueDeadlines = deadlineRepository.findOverdueDeadlines(now);

        for (WorkflowDeadline deadline : overdueDeadlines) {
            try {
                if (!deadline.getOverdueSent()) {
                    sendOverdueNotification(deadline);
                    deadline.setOverdueSent(true);
                    deadlineRepository.save(deadline);
                }

                // Check if escalation is needed
                checkAndEscalate(deadline);
            } catch (Exception e) {
                log.error("Failed to process overdue deadline {}: {}", deadline.getId(), e.getMessage());
            }
        }

        log.info("Processed {} overdue deadlines", overdueDeadlines.size());
    }

    /**
     * Escalate a deadline. Respects the configured {@link #maxEscalationLevel} cap —
     * past the cap this is a no-op so manual reruns can't push a stuck workflow into a loop.
     */
    public void escalate(UUID deadlineId) {
        log.debug("Escalating deadline {}", deadlineId);

        WorkflowDeadline deadline = deadlineRepository
            .findById(deadlineId)
            .orElseThrow(() -> EntityNotFoundException.create("WorkflowDeadline", deadlineId));

        if (deadline.getEscalationLevel() != null && deadline.getEscalationLevel() >= maxEscalationLevel) {
            log.warn("Deadline {} already at max escalation level {}; refusing further escalation", deadlineId, maxEscalationLevel);
            return;
        }

        applyEscalation(deadline);
    }

    /**
     * Get overdue deadlines count for a workflow.
     */
    @Transactional(readOnly = true)
    public long countOverdueDeadlines(UUID workflowId) {
        return deadlineRepository.countOverdueByWorkflowId(workflowId, Instant.now());
    }

    /**
     * Check and escalate overdue deadline if needed. Bounded by {@link #maxEscalationLevel}
     * so the hourly scheduler can't loop forever on a stuck workflow.
     */
    private void checkAndEscalate(WorkflowDeadline deadline) {
        int currentLevel = deadline.getEscalationLevel() != null ? deadline.getEscalationLevel() : 0;
        if (currentLevel >= maxEscalationLevel) {
            return; // Past the cap; quiet no-op so we don't log-spam on the hourly tick.
        }

        Instant now = Instant.now();
        long hoursOverdue = ChronoUnit.HOURS.between(deadline.getDeadlineAt(), now);
        // Escalate every 24 hours overdue, clamped to the cap.
        int expectedEscalationLevel = Math.min((int) (hoursOverdue / 24), maxEscalationLevel);

        if (expectedEscalationLevel > currentLevel) {
            applyEscalation(deadline);
        }
    }

    /**
     * Single chokepoint for committing an escalation: bumps the level, persists, notifies,
     * and publishes {@link EscalationTriggeredEvent} so other listeners can react without
     * coupling to this service. Callers must verify the cap before invoking.
     */
    private void applyEscalation(WorkflowDeadline deadline) {
        deadline.escalate();
        WorkflowDeadline saved = deadlineRepository.save(deadline);
        sendEscalationNotification(saved);
        UUID assigneeId = saved.getAssignee() != null ? saved.getAssignee().getId() : null;
        UUID workflowId = saved.getWorkflowInstance() != null ? saved.getWorkflowInstance().getId() : null;
        eventPublisher.publishEvent(
            EscalationTriggeredEvent.of(saved.getId(), TenantContext.getCurrentTenant(), workflowId, saved.getEscalationLevel(), assigneeId)
        );
        log.info("Escalated deadline {} to level {} (cap={})", saved.getId(), saved.getEscalationLevel(), maxEscalationLevel);
    }

    /**
     * Send warning notification for approaching deadline.
     */
    private void sendWarningNotification(WorkflowDeadline deadline) {
        if (deadline.getAssignee() != null) {
            notificationService.notifyDeadlineApproaching(
                deadline.getAssignee().getId(),
                "Deadline Approaching: " + deadline.getDeadlineType(),
                deadline.getDescription(),
                deadline.getWorkflowInstance().getEntityId(),
                deadline.getWorkflowInstance().getEntityType(),
                deadline.getDeadlineAt()
            );
        }
    }

    /**
     * Send overdue notification.
     */
    private void sendOverdueNotification(WorkflowDeadline deadline) {
        if (deadline.getAssignee() != null) {
            notificationService.notifyDeadlineExceeded(
                deadline.getAssignee().getId(),
                "Deadline Exceeded: " + deadline.getDeadlineType(),
                deadline.getDescription() + " - This item is now overdue!",
                deadline.getWorkflowInstance().getEntityId(),
                deadline.getWorkflowInstance().getEntityType()
            );
        }
    }

    /**
     * Send escalation notification.
     */
    private void sendEscalationNotification(WorkflowDeadline deadline) {
        // Notify the assignee's manager if available
        if (deadline.getAssignee() != null && deadline.getAssignee().getManager() != null) {
            notificationService.notifyEscalation(
                deadline.getAssignee().getManager().getId(),
                "Escalation: " + deadline.getDeadlineType(),
                "Deadline has been escalated for " +
                deadline.getAssignee().getFirstName() +
                " " +
                deadline.getAssignee().getLastName() +
                ": " +
                deadline.getDescription(),
                deadline.getWorkflowInstance().getEntityId(),
                deadline.getWorkflowInstance().getEntityType(),
                deadline.getEscalationLevel()
            );
        }
    }
}
