package com.humano.service.billing;

import com.humano.service.MailService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

/**
 * P4.3 — Billing-flavoured email service.
 * <p>
 * Renders Thymeleaf templates under {@code templates/mail/billing/*} with a
 * small typed context and delegates the actual SMTP send to
 * {@link MailService#sendEmail}, which is {@code @Async} — so any caller of
 * this service is guaranteed not to block its own transaction on email
 * delivery (invariant I5).
 * <p>
 * Each {@code send*} method takes the minimum primitives needed for the
 * template (no entity references — keeps rendering outside any JPA
 * lazy-init hot path, mirrors the P3.5 PayslipPdfModel pattern). Subjects
 * come from {@code messages*.properties} via {@link MessageSource}, so
 * future locale support is a property-file change, not a code change.
 * <p>
 * Templates referenced:
 * <ul>
 *   <li>{@code mail/billing/welcome}</li>
 *   <li>{@code mail/billing/invoiceIssued}</li>
 *   <li>{@code mail/billing/paymentReceipt}</li>
 *   <li>{@code mail/billing/paymentFailed}</li>
 *   <li>{@code mail/billing/subscriptionCancelled}</li>
 *   <li>{@code mail/billing/subscriptionRenewed}</li>
 *   <li>{@code mail/billing/trialEnding}</li>
 * </ul>
 */
@Service
public class BillingMailService {

    private static final Logger LOG = LoggerFactory.getLogger(BillingMailService.class);

    private static final DateTimeFormatter HUMAN_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MailService mailService;
    private final MessageSource messageSource;
    private final SpringTemplateEngine templateEngine;
    private final JHipsterProperties jHipsterProperties;

    public BillingMailService(
        MailService mailService,
        MessageSource messageSource,
        SpringTemplateEngine templateEngine,
        JHipsterProperties jHipsterProperties
    ) {
        this.mailService = mailService;
        this.messageSource = messageSource;
        this.templateEngine = templateEngine;
        this.jHipsterProperties = jHipsterProperties;
    }

    public void sendWelcome(String to, String tenantName, String subdomain, String adminFirstName, boolean trial) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("subdomain", subdomain);
        vars.put("firstName", adminFirstName);
        vars.put("trial", trial);
        dispatch(to, "mail/billing/welcome", "email.billing.welcome.title", vars);
    }

    public void sendInvoiceIssued(
        String to,
        String tenantName,
        String invoiceNumber,
        BigDecimal totalAmount,
        String currency,
        Instant dueDate
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("invoiceNumber", invoiceNumber);
        vars.put("totalAmount", totalAmount);
        vars.put("currency", currency);
        vars.put("dueDate", formatInstant(dueDate));
        dispatch(to, "mail/billing/invoiceIssued", "email.billing.invoice-issued.title", vars);
    }

    public void sendPaymentReceipt(
        String to,
        String tenantName,
        String invoiceNumber,
        BigDecimal amount,
        String currency,
        String externalPaymentId
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("invoiceNumber", invoiceNumber);
        vars.put("amount", amount);
        vars.put("currency", currency);
        vars.put("externalPaymentId", externalPaymentId);
        dispatch(to, "mail/billing/paymentReceipt", "email.billing.payment-receipt.title", vars);
    }

    public void sendPaymentFailed(
        String to,
        String tenantName,
        String invoiceNumber,
        BigDecimal amount,
        String currency,
        String failureReason
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("invoiceNumber", invoiceNumber);
        vars.put("amount", amount);
        vars.put("currency", currency);
        vars.put("failureReason", failureReason != null ? failureReason : "Unknown");
        dispatch(to, "mail/billing/paymentFailed", "email.billing.payment-failed.title", vars);
    }

    public void sendSubscriptionCancelled(String to, String tenantName, String planName, Instant effectiveAt) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("planName", planName);
        vars.put("effectiveAt", formatInstant(effectiveAt));
        dispatch(to, "mail/billing/subscriptionCancelled", "email.billing.subscription-cancelled.title", vars);
    }

    public void sendSubscriptionRenewed(
        String to,
        String tenantName,
        String planName,
        Instant nextRenewalAt,
        BigDecimal amount,
        String currency
    ) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("planName", planName);
        vars.put("nextRenewalAt", formatInstant(nextRenewalAt));
        vars.put("amount", amount);
        vars.put("currency", currency);
        dispatch(to, "mail/billing/subscriptionRenewed", "email.billing.subscription-renewed.title", vars);
    }

    public void sendTrialEnding(String to, String tenantName, Instant trialEndsAt, long daysRemaining) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("tenantName", tenantName);
        vars.put("trialEndsAt", formatInstant(trialEndsAt));
        vars.put("daysRemaining", daysRemaining);
        dispatch(to, "mail/billing/trialEnding", "email.billing.trial-ending.title", vars);
    }

    private void dispatch(String to, String templateName, String titleKey, Map<String, Object> vars) {
        if (to == null || to.isBlank()) {
            LOG.warn("Billing email '{}' skipped: empty recipient", templateName);
            return;
        }
        Locale locale = Locale.ENGLISH;
        Context context = new Context(locale);
        context.setVariable("baseUrl", jHipsterProperties.getMail().getBaseUrl());
        vars.forEach(context::setVariable);
        String body = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        // MailService.sendEmail is @Async — returns immediately, send happens
        // on the application's TaskExecutor.
        mailService.sendEmail(to, subject, body, false, true);
        LOG.debug("Queued billing email '{}' to '{}'", templateName, to);
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return HUMAN_DATE.format(LocalDate.ofInstant(instant, ZoneOffset.UTC));
    }
}
