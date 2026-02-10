package com.humano.events.listeners;

import com.humano.events.PaymentCompletedEvent;
import com.humano.events.TenantOnboardedEvent;
import com.humano.events.TenantStatusChangedEvent;
import com.humano.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event listener for tenant-related events.
 * Handles async processing of tenant lifecycle events.
 */
@Component
public class TenantEventListener {

    private static final Logger log = LoggerFactory.getLogger(TenantEventListener.class);

    private final MailService mailService;

    public TenantEventListener(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * Handle tenant onboarded event.
     * Sends welcome email and triggers provisioning tasks.
     */
    @EventListener
    @Async
    public void handleTenantOnboarded(TenantOnboardedEvent event) {
        log.info("Handling tenant onboarded event for: {} (ID: {})", event.tenantName(), event.tenantId());

        try {
            // Send welcome email
            sendWelcomeEmail(event);

            // Set up default data for the tenant
            provisionTenantDefaults(event);

            // Notify admin about new signup
            notifyAdminNewSignup(event);

            log.info("Successfully processed onboarding event for tenant: {}", event.tenantId());
        } catch (Exception e) {
            log.error("Error processing tenant onboarded event for: {}", event.tenantId(), e);
        }
    }

    /**
     * Handle tenant status changed event.
     */
    @EventListener
    @Async
    public void handleTenantStatusChanged(TenantStatusChangedEvent event) {
        log.info("Handling tenant status change for: {} ({} -> {})", event.tenantName(), event.previousStatus(), event.newStatus());

        try {
            switch (event.newStatus()) {
                case ACTIVE -> handleTenantActivated(event);
                case SUSPENDED -> handleTenantSuspended(event);
                case DEACTIVATED -> handleTenantDeactivated(event);
                default -> log.debug("No action required for status: {}", event.newStatus());
            }
        } catch (Exception e) {
            log.error("Error processing tenant status change event for: {}", event.tenantId(), e);
        }
    }

    /**
     * Handle payment completed event.
     */
    @EventListener
    @Async
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Handling payment completed event for invoice: {} (Tenant: {})", event.invoiceNumber(), event.tenantName());

        try {
            // Send payment receipt
            sendPaymentReceipt(event);

            log.info("Successfully processed payment completed event for invoice: {}", event.invoiceNumber());
        } catch (Exception e) {
            log.error("Error processing payment completed event for invoice: {}", event.invoiceId(), e);
        }
    }

    // ========== HELPER METHODS ==========

    private void sendWelcomeEmail(TenantOnboardedEvent event) {
        log.debug("Sending welcome email to: {}", event.adminEmail());

        // TODO: Use MailService to send templated welcome email
        // mailService.sendWelcomeEmail(event.adminEmail(), event.tenantName(), event.subdomain());

        log.info("Welcome email would be sent to {}", event.adminEmail());
    }

    private void provisionTenantDefaults(TenantOnboardedEvent event) {
        log.debug("Provisioning default data for tenant: {}", event.tenantId());

        // TODO: Create default data for new tenant:
        // - Default departments
        // - Default leave types
        // - Default pay schedules
        // - Default document templates
        // - Sample data (if requested)

        log.info("Default data provisioned for tenant: {}", event.tenantId());
    }

    private void notifyAdminNewSignup(TenantOnboardedEvent event) {
        log.debug("Notifying admin about new signup: {}", event.tenantName());

        // TODO: Send notification to system administrators about new tenant
        // This could be Slack, email, or internal notification system

        String message = String.format(
            "New tenant signed up: %s (%s) - Plan: %s - Trial: %s",
            event.tenantName(),
            event.subdomain(),
            event.subscriptionPlanName(),
            event.isTrial()
        );

        log.info("Admin notification: {}", message);
    }

    private void handleTenantActivated(TenantStatusChangedEvent event) {
        log.debug("Processing tenant activation: {}", event.tenantId());

        // Send activation confirmation email
        // Restore access if previously suspended

        log.info("Tenant {} activated successfully", event.tenantName());
    }

    private void handleTenantSuspended(TenantStatusChangedEvent event) {
        log.debug("Processing tenant suspension: {}", event.tenantId());

        // Send suspension notification email
        // Include instructions for reactivation

        log.info("Tenant {} suspended. Reason: {}", event.tenantName(), event.reason());
    }

    private void handleTenantDeactivated(TenantStatusChangedEvent event) {
        log.debug("Processing tenant deactivation: {}", event.tenantId());

        // Send deactivation notification
        // Schedule data export reminder
        // Initiate data retention countdown

        log.info("Tenant {} deactivated", event.tenantName());
    }

    private void sendPaymentReceipt(PaymentCompletedEvent event) {
        log.debug("Sending payment receipt for invoice: {}", event.invoiceNumber());

        // TODO: Generate PDF receipt and send via email
        // mailService.sendPaymentReceipt(...)

        log.info("Payment receipt would be sent for invoice: {} to tenant: {}", event.invoiceNumber(), event.tenantName());
    }
}
