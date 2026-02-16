package com.humano.service.hr.workflow.infrastructure;

import com.humano.domain.hr.Employee;
import com.humano.domain.hr.EmployeeNotification;
import com.humano.repository.hr.EmployeeNotificationRepository;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for orchestrating notifications across all workflows.
 * Handles sending notifications for approvals, tasks, deadlines, and workflow events.
 */
@Service
@Transactional
public class NotificationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrationService.class);

    private final EmployeeNotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;

    public NotificationOrchestrationService(EmployeeNotificationRepository notificationRepository, EmployeeRepository employeeRepository) {
        this.notificationRepository = notificationRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Notify an employee that their approval is required.
     */
    public void notifyApprovalRequired(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending approval required notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message);
    }

    /**
     * Notify an employee of an approval decision.
     */
    public void notifyApprovalDecision(
        UUID employeeId,
        String title,
        String message,
        UUID relatedEntityId,
        String entityType,
        boolean approved
    ) {
        log.debug("Sending approval decision notification to employee {}: approved={}", employeeId, approved);
        String status = approved ? "Approved" : "Rejected";
        createNotification(employeeId, title + " - " + status + ": " + message);
    }

    /**
     * Notify an employee of a task assignment.
     */
    public void notifyTaskAssignment(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending task assignment notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message);
    }

    /**
     * Notify an employee of an approaching deadline.
     */
    public void notifyDeadlineApproaching(
        UUID employeeId,
        String title,
        String message,
        UUID relatedEntityId,
        String entityType,
        Instant deadline
    ) {
        log.debug("Sending deadline approaching notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message + " (Due: " + deadline + ")");
    }

    /**
     * Notify an employee of an exceeded deadline.
     */
    public void notifyDeadlineExceeded(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending deadline exceeded notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message);
    }

    /**
     * Notify about workflow completion.
     */
    public void notifyWorkflowCompleted(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending workflow completed notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message);
    }

    /**
     * Notify about an escalation.
     */
    public void notifyEscalation(
        UUID employeeId,
        String title,
        String message,
        UUID relatedEntityId,
        String entityType,
        int escalationLevel
    ) {
        log.debug("Sending escalation notification to employee {} (level {})", employeeId, escalationLevel);
        createNotification(employeeId, title + " (Level " + escalationLevel + "): " + message);
    }

    /**
     * Send a reminder notification.
     */
    public void sendReminder(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending reminder notification to employee {}", employeeId);
        createNotification(employeeId, "Reminder - " + title + ": " + message);
    }

    /**
     * Notify about task completion.
     */
    public void notifyTaskCompleted(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending task completed notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message);
    }

    /**
     * Send welcome notification.
     */
    public void notifyWelcome(UUID employeeId, String title, String message) {
        log.debug("Sending welcome notification to employee {}", employeeId);
        createNotification(employeeId, title + ": " + message);
    }

    /**
     * Send bulk notifications to multiple employees.
     */
    public void sendBulkNotification(List<UUID> employeeIds, String title, String message) {
        log.debug("Sending bulk notification to {} employees", employeeIds.size());
        for (UUID employeeId : employeeIds) {
            try {
                createNotification(employeeId, title + ": " + message);
            } catch (Exception e) {
                log.error("Failed to send notification to employee {}: {}", employeeId, e.getMessage());
            }
        }
    }

    /**
     * Create and save a notification.
     */
    private void createNotification(UUID employeeId, String message) {
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));

        EmployeeNotification notification = new EmployeeNotification();
        notification.setEmployee(employee);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());

        notificationRepository.save(notification);
        log.info("Created notification for employee {}", employeeId);
    }
}
