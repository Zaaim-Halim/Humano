package com.humano.service.hr.workflow.infrastructure;

import com.humano.domain.hr.EmployeeNotification;
import com.humano.domain.shared.Employee;
import com.humano.repository.hr.EmployeeNotificationRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.MailService;
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
 *
 * <p>Two channels are wired here: an in-app row inserted into
 * {@code employee_notification}, and an email dispatched through {@link MailService}
 * (which is {@code @Async}, so the SMTP call escapes the caller's transaction).
 * Push notifications are deferred until the mobile client lands.
 */
@Service
@Transactional
public class NotificationOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOrchestrationService.class);

    private final EmployeeNotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final MailService mailService;

    public NotificationOrchestrationService(
        EmployeeNotificationRepository notificationRepository,
        EmployeeRepository employeeRepository,
        MailService mailService
    ) {
        this.notificationRepository = notificationRepository;
        this.employeeRepository = employeeRepository;
        this.mailService = mailService;
    }

    /**
     * Notify an employee that their approval is required.
     */
    public void notifyApprovalRequired(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending approval required notification to employee {}", employeeId);
        notify(employeeId, title, title + ": " + message);
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
        notify(employeeId, title + " - " + status, title + " - " + status + ": " + message);
    }

    /**
     * Notify an employee of a task assignment.
     */
    public void notifyTaskAssignment(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending task assignment notification to employee {}", employeeId);
        notify(employeeId, title, title + ": " + message);
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
        notify(employeeId, title, title + ": " + message + " (Due: " + deadline + ")");
    }

    /**
     * Notify an employee of an exceeded deadline.
     */
    public void notifyDeadlineExceeded(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending deadline exceeded notification to employee {}", employeeId);
        notify(employeeId, title, title + ": " + message);
    }

    /**
     * Notify about workflow completion. In-app only — workflow-completed signals are noisy
     * and don't justify cluttering the inbox.
     */
    public void notifyWorkflowCompleted(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending workflow completed notification to employee {}", employeeId);
        sendInAppNotification(employeeId, title + ": " + message);
    }

    /**
     * Notify about an escalation. Always emails — by definition someone failed to act
     * in time and the manager needs an out-of-band signal.
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
        String subject = title + " (Level " + escalationLevel + ")";
        notify(employeeId, subject, subject + ": " + message);
    }

    /**
     * Send a reminder notification.
     */
    public void sendReminder(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending reminder notification to employee {}", employeeId);
        notify(employeeId, "Reminder - " + title, "Reminder - " + title + ": " + message);
    }

    /**
     * Notify about task completion. In-app only — same noise rationale as
     * {@link #notifyWorkflowCompleted}.
     */
    public void notifyTaskCompleted(UUID employeeId, String title, String message, UUID relatedEntityId, String entityType) {
        log.debug("Sending task completed notification to employee {}", employeeId);
        sendInAppNotification(employeeId, title + ": " + message);
    }

    /**
     * Send welcome notification — both channels.
     */
    public void notifyWelcome(UUID employeeId, String title, String message) {
        log.debug("Sending welcome notification to employee {}", employeeId);
        notify(employeeId, title, title + ": " + message);
    }

    /**
     * Send bulk notifications to multiple employees. In-app only — bulk emails would amplify
     * any rendering bug into N user inboxes; reserve email for per-recipient pathways.
     */
    public void sendBulkNotification(List<UUID> employeeIds, String title, String message) {
        log.debug("Sending bulk notification to {} employees", employeeIds.size());
        for (UUID employeeId : employeeIds) {
            try {
                sendInAppNotification(employeeId, title + ": " + message);
            } catch (Exception e) {
                log.error("Failed to send notification to employee {}: {}", employeeId, e.getMessage());
            }
        }
    }

    /**
     * Convenience: dispatch on both channels. Resolves the employee once, then fans out.
     */
    private void notify(UUID employeeId, String subject, String inAppMessage) {
        Employee employee = loadEmployee(employeeId);
        persistInAppNotification(employee, inAppMessage);
        sendEmail(employee, subject, inAppMessage);
    }

    /**
     * Insert an {@code EmployeeNotification} row. Single-channel callers (welcome/bulk/
     * task-completed) hit this directly.
     */
    private void sendInAppNotification(UUID employeeId, String message) {
        persistInAppNotification(loadEmployee(employeeId), message);
    }

    private void persistInAppNotification(Employee employee, String message) {
        EmployeeNotification notification = new EmployeeNotification();
        notification.setEmployee(employee);
        notification.setMessage(message);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
        log.info("Created in-app notification for employee {}", employee.getId());
    }

    /**
     * Hand off to {@link MailService#sendEmail} which is {@code @Async} — the SMTP call
     * happens on a worker thread after the calling transaction commits/rolls back, so we
     * never block the workflow tick on the mail server and we never blow up the request
     * if the mail server is down.
     */
    private void sendEmail(Employee employee, String subject, String body) {
        String to = employee.getEmail();
        if (to == null || to.isBlank()) {
            log.warn("Skipping email for employee {}: no address on record", employee.getId());
            return;
        }
        mailService.sendEmail(to, subject, body, false, false);
    }

    private Employee loadEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId).orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
    }
}
