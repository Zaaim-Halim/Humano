package com.humano.service.billing;

import com.humano.domain.billing.BillingCurrency;
import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Payment;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.PaymentStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.billing.requests.CreatePaymentRequest;
import com.humano.dto.billing.responses.PaymentResponse;
import com.humano.events.PaymentCompletedEvent;
import com.humano.events.PaymentFailedEvent;
import com.humano.repository.billing.BillingCurrencyRepository;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.PaymentRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.payment.PaymentProvider;
import com.humano.service.billing.payment.PaymentProviderException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing payments.
 * Handles CRUD operations, payment processing, and tenant activation on successful payment.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingCurrencyRepository currencyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;
    /**
     * P4.2 — Optional. Resolved on demand via {@link ObjectProvider#getIfAvailable()};
     * absent in dev when {@code humano.billing.stripe.secret-key} is empty. When
     * absent, {@link #refundPayment} and {@link #retryPayment} keep the simulate-
     * success path so the app boots and the REST surface stays usable without
     * Stripe credentials. In prod the bean is always present.
     */
    private final ObjectProvider<PaymentProvider> paymentProvider;

    public PaymentService(
        PaymentRepository paymentRepository,
        InvoiceRepository invoiceRepository,
        BillingCurrencyRepository currencyRepository,
        SubscriptionRepository subscriptionRepository,
        TenantRepository tenantRepository,
        ApplicationEventPublisher eventPublisher,
        ObjectProvider<PaymentProvider> paymentProvider
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.currencyRepository = currencyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
        this.paymentProvider = paymentProvider;
    }

    /**
     * Create a new payment.
     *
     * @param request the payment creation request
     * @return the created payment response
     */
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.debug("Request to create Payment: {}", request);

        Invoice invoice = invoiceRepository
            .findById(request.invoiceId())
            .orElseThrow(() -> EntityNotFoundException.create("Invoice", request.invoiceId()));

        BillingCurrency currency = currencyRepository
            .findById(request.currencyId())
            .orElseThrow(() -> EntityNotFoundException.create("Currency", request.currencyId()));

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(request.amount());
        payment.setMethodType(request.methodType());
        payment.setCurrency(currency);
        payment.setExternalPaymentId(request.externalPaymentId());
        payment.setPaymentDate(Instant.now());
        payment.setStatus(PaymentStatus.PENDING);

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Created payment with ID: {}", savedPayment.getId());

        return mapToResponse(savedPayment);
    }

    /**
     * Complete a payment and handle tenant/subscription activation.
     *
     * @param id the ID of the payment to complete
     * @return the updated payment response
     */
    @Transactional
    public PaymentResponse completePayment(UUID id) {
        log.debug("Request to complete Payment: {}", id);

        return paymentRepository
            .findById(id)
            .map(payment -> {
                payment.setStatus(PaymentStatus.COMPLETED);

                // Check if the invoice is fully paid
                Invoice invoice = payment.getInvoice();
                BigDecimal totalPaid = invoice
                    .getPayments()
                    .stream()
                    .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(payment.getAmount());

                if (totalPaid.compareTo(invoice.getTotalAmount()) >= 0) {
                    invoice.setStatus(InvoiceStatus.PAID);
                    invoice.setPaidDate(Instant.now());
                    invoiceRepository.save(invoice);

                    // Activate tenant and subscription if this is an initial or renewal payment
                    activateTenantIfNeeded(invoice);
                }

                Payment savedPayment = paymentRepository.save(payment);

                // Publish payment completed event
                publishPaymentCompletedEvent(savedPayment, invoice);

                return mapToResponse(savedPayment);
            })
            .orElseThrow(() -> EntityNotFoundException.create("Payment", id));
    }

    /**
     * Activate tenant and subscription if they are pending payment.
     */
    private void activateTenantIfNeeded(Invoice invoice) {
        Subscription subscription = invoice.getSubscription();
        Tenant tenant = invoice.getTenant();

        // Activate subscription if pending
        if (
            subscription.getStatus() == SubscriptionStatus.PENDING_PAYMENT ||
            subscription.getStatus() == SubscriptionStatus.SUSPENDED ||
            subscription.getStatus() == SubscriptionStatus.PAST_DUE
        ) {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);
            log.info("Activated subscription: {} after payment", subscription.getId());
        }

        // Activate tenant if pending or suspended
        if (tenant.getStatus() == TenantStatus.PENDING_SETUP || tenant.getStatus() == TenantStatus.SUSPENDED) {
            tenant.setStatus(TenantStatus.ACTIVE);
            tenantRepository.save(tenant);
            log.info("Activated tenant: {} after payment", tenant.getId());
        }
    }

    /**
     * Publish payment completed event for async processing.
     */
    private void publishPaymentCompletedEvent(Payment payment, Invoice invoice) {
        Tenant tenant = invoice.getTenant();
        PaymentCompletedEvent event = PaymentCompletedEvent.of(
            payment.getId(),
            invoice.getId(),
            invoice.getInvoiceNumber(),
            tenant.getId(),
            tenant.getName(),
            payment.getAmount(),
            payment.getCurrency() != null ? payment.getCurrency().getCode().name() : "USD",
            payment.getMethodType().name(),
            payment.getExternalPaymentId()
        );
        eventPublisher.publishEvent(event);
        log.debug("Published payment completed event for payment: {}", payment.getId());
    }

    /**
     * Process a refund for a payment.
     *
     * @param id the ID of the payment to refund
     * @param refundAmount the amount to refund (null for full refund)
     * @return the updated payment response
     */
    @Transactional
    public PaymentResponse refundPayment(UUID id, BigDecimal refundAmount) {
        log.debug("Request to refund Payment: {} (amount: {})", id, refundAmount);

        return paymentRepository
            .findById(id)
            .map(payment -> {
                if (payment.getStatus() != PaymentStatus.COMPLETED) {
                    throw new IllegalStateException("Can only refund completed payments");
                }

                BigDecimal actualRefundAmount = refundAmount != null ? refundAmount : payment.getAmount();
                BigDecimal currentRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO;
                BigDecimal totalRefundable = payment.getAmount().subtract(currentRefunded);

                if (actualRefundAmount.compareTo(totalRefundable) > 0) {
                    throw new IllegalStateException("Refund amount exceeds refundable balance");
                }

                payment.setRefundedAmount(currentRefunded.add(actualRefundAmount));

                // Mark as fully refunded if applicable
                if (payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                }

                // P4.2 — call the provider if both wired (provider bean) and applicable
                // (externalPaymentId carries a Stripe charge id, not the sim "retry_..." marker).
                PaymentProvider provider = paymentProvider.getIfAvailable();
                if (provider != null && payment.getExternalPaymentId() != null && payment.getExternalPaymentId().startsWith("pi_")) {
                    try {
                        PaymentProvider.RefundResult result = provider.refund(payment.getExternalPaymentId(), actualRefundAmount);
                        mergeMetadata(payment, "refund", result.providerMetadata());
                        log.info("Stripe refund {} succeeded for payment: {}", result.refundId(), id);
                    } catch (PaymentProviderException e) {
                        log.warn("Stripe refund failed for payment {} ({}): {}", id, e.getKind(), e.getMessage());
                        throw new IllegalStateException("Provider refund failed: " + e.getMessage(), e);
                    }
                }

                log.info("Processed refund of {} for payment: {}", actualRefundAmount, id);
                return mapToResponse(paymentRepository.save(payment));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Payment", id));
    }

    /**
     * Merge a provider response snapshot into the payment's accumulating metadata,
     * keyed by operation ("charge", "refund", "retry", "webhook"). Each operation
     * overwrites its prior snapshot — we don't keep a per-attempt history here
     * (the audit event log P6.2 covers that level of detail).
     */
    private void mergeMetadata(Payment payment, String operation, java.util.Map<String, Object> snapshot) {
        java.util.Map<String, Object> existing = payment.getProviderMetadata();
        if (existing == null) {
            existing = new HashMap<>();
        }
        existing.put(operation, snapshot);
        existing.put("lastOperation", operation);
        existing.put("lastOperationAt", Instant.now().toString());
        payment.setProviderMetadata(existing);
    }

    /**
     * Retry a failed payment.
     *
     * @param id the ID of the payment to retry
     * @param newPaymentToken new payment token from payment provider
     * @return the updated payment response
     */
    @Transactional
    public PaymentResponse retryPayment(UUID id, String newPaymentToken) {
        log.debug("Request to retry Payment: {}", id);

        return paymentRepository
            .findById(id)
            .map(payment -> {
                if (payment.getStatus() != PaymentStatus.FAILED) {
                    throw new IllegalStateException("Can only retry failed payments");
                }

                // Increment retry count
                int retryCount = payment.getRetryCount() != null ? payment.getRetryCount() : 0;
                payment.setRetryCount(retryCount + 1);

                // P4.2 — provider call if wired. Idempotency key includes the retry count
                // so each attempt is distinct (otherwise Stripe replays the failed result).
                PaymentProvider provider = paymentProvider.getIfAvailable();
                if (provider != null) {
                    String idempotencyKey = "payment-" + id + "-retry-" + payment.getRetryCount();
                    String currencyCode = payment.getCurrency() != null ? payment.getCurrency().getCode().name() : "USD";
                    try {
                        PaymentProvider.ChargeResult result = provider.charge(
                            payment.getAmount(),
                            currencyCode,
                            newPaymentToken,
                            idempotencyKey
                        );
                        payment.setExternalPaymentId(result.transactionId());
                        mergeMetadata(payment, "retry", result.providerMetadata());
                        if (isProviderSuccessStatus(result.status())) {
                            payment.setStatus(PaymentStatus.COMPLETED);
                            payment.setPaymentDate(Instant.now());
                            payment.setFailureReason(null);
                        } else {
                            // Provider accepted but requires next action (3DS, etc.) — leave PENDING.
                            payment.setStatus(PaymentStatus.PENDING);
                            payment.setFailureReason("Provider status: " + result.status());
                            return mapToResponse(paymentRepository.save(payment));
                        }
                    } catch (PaymentProviderException e) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setFailureReason(e.getMessage());
                        Payment saved = paymentRepository.save(payment);
                        publishPaymentFailedEvent(saved, payment.getInvoice(), e.getProviderCode());
                        log.warn("Stripe retry failed for payment {} ({}): {}", id, e.getKind(), e.getMessage());
                        return mapToResponse(saved);
                    }
                } else {
                    // Dev / no-provider path — simulate success (legacy behaviour).
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setPaymentDate(Instant.now());
                    payment.setExternalPaymentId("retry_" + UUID.randomUUID().toString().substring(0, 8));
                    payment.setFailureReason(null);
                }

                Payment savedPayment = paymentRepository.save(payment);

                // Update invoice if fully paid
                Invoice invoice = payment.getInvoice();
                BigDecimal totalPaid = invoice
                    .getPayments()
                    .stream()
                    .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalPaid.compareTo(invoice.getTotalAmount()) >= 0) {
                    invoice.setStatus(InvoiceStatus.PAID);
                    invoice.setPaidDate(Instant.now());
                    invoiceRepository.save(invoice);
                    activateTenantIfNeeded(invoice);
                }

                publishPaymentCompletedEvent(savedPayment, invoice);

                log.info("Successfully retried payment: {}", id);
                return mapToResponse(savedPayment);
            })
            .orElseThrow(() -> EntityNotFoundException.create("Payment", id));
    }

    /**
     * Fail a payment.
     *
     * @param id the ID of the payment to fail
     * @param failureReason the reason for failure
     * @return the updated payment response
     */
    @Transactional
    public PaymentResponse failPayment(UUID id, String failureReason) {
        log.debug("Request to fail Payment: {} with reason: {}", id, failureReason);

        return paymentRepository
            .findById(id)
            .map(payment -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(failureReason);
                return mapToResponse(paymentRepository.save(payment));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Payment", id));
    }

    /**
     * Get a payment by ID.
     *
     * @param id the ID of the payment
     * @return the payment response
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(UUID id) {
        log.debug("Request to get Payment by ID: {}", id);

        return paymentRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Payment", id));
    }

    /**
     * Get all payments with pagination.
     *
     * @param pageable pagination information
     * @return page of payment responses
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        log.debug("Request to get all Payments");

        return paymentRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get payments by invoice.
     *
     * @param invoiceId the invoice ID
     * @return list of payment responses
     */
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByInvoice(UUID invoiceId) {
        log.debug("Request to get Payments by Invoice: {}", invoiceId);

        return paymentRepository.findByInvoiceId(invoiceId).stream().map(this::mapToResponse).toList();
    }

    /**
     * Delete a payment by ID.
     *
     * @param id the ID of the payment to delete
     */
    @Transactional
    public void deletePayment(UUID id) {
        log.debug("Request to delete Payment: {}", id);

        if (!paymentRepository.existsById(id)) {
            throw EntityNotFoundException.create("Payment", id);
        }
        paymentRepository.deleteById(id);
        log.info("Deleted payment with ID: {}", id);
    }

    private static boolean isProviderSuccessStatus(String status) {
        return "succeeded".equalsIgnoreCase(status) || "captured".equalsIgnoreCase(status);
    }

    /**
     * P4.2 — Webhook entry point. Stripe's {@code payment_intent.succeeded} fires
     * asynchronously after a charge clears (3DS challenge, off-session debit, etc.).
     * The webhook resource resolves the {@code PaymentIntent.id} back to our
     * {@link Payment} via {@link PaymentRepository#findByExternalPaymentId} and
     * calls this method. Idempotent: a duplicate webhook on an already-COMPLETED
     * payment is a no-op.
     */
    @Transactional
    public Optional<PaymentResponse> completeByExternalId(String externalPaymentId, java.util.Map<String, Object> webhookSnapshot) {
        log.info("Webhook completePaymentByExternalId: {}", externalPaymentId);
        return paymentRepository
            .findByExternalPaymentId(externalPaymentId)
            .map(payment -> {
                if (payment.getStatus() == PaymentStatus.COMPLETED) {
                    log.debug("Payment {} already COMPLETED — webhook is replay; no-op", payment.getId());
                    return mapToResponse(payment);
                }
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setPaymentDate(Instant.now());
                payment.setFailureReason(null);
                mergeMetadata(payment, "webhook", webhookSnapshot);

                Invoice invoice = payment.getInvoice();
                BigDecimal totalPaid = invoice
                    .getPayments()
                    .stream()
                    .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(payment.getAmount());

                if (totalPaid.compareTo(invoice.getTotalAmount()) >= 0) {
                    invoice.setStatus(InvoiceStatus.PAID);
                    invoice.setPaidDate(Instant.now());
                    invoiceRepository.save(invoice);
                    activateTenantIfNeeded(invoice);
                }

                Payment saved = paymentRepository.save(payment);
                publishPaymentCompletedEvent(saved, invoice);
                return mapToResponse(saved);
            });
    }

    /**
     * P4.2 — Webhook entry point. Stripe's {@code payment_intent.payment_failed}
     * marks the corresponding payment FAILED and publishes
     * {@link PaymentFailedEvent} for dunning + email listeners. Idempotent.
     */
    @Transactional
    public Optional<PaymentResponse> failByExternalId(
        String externalPaymentId,
        String failureReason,
        String providerCode,
        java.util.Map<String, Object> webhookSnapshot
    ) {
        log.info("Webhook failPaymentByExternalId: {} ({})", externalPaymentId, failureReason);
        return paymentRepository
            .findByExternalPaymentId(externalPaymentId)
            .map(payment -> {
                if (payment.getStatus() == PaymentStatus.FAILED) {
                    log.debug("Payment {} already FAILED — webhook is replay; no-op", payment.getId());
                    return mapToResponse(payment);
                }
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(failureReason);
                mergeMetadata(payment, "webhook", webhookSnapshot);
                Payment saved = paymentRepository.save(payment);
                publishPaymentFailedEvent(saved, payment.getInvoice(), providerCode);
                return mapToResponse(saved);
            });
    }

    /**
     * P4.2 — Webhook entry point for {@code charge.refunded}. Pushes the refunded
     * amount onto the payment row and re-evaluates terminal status. Idempotent on
     * the {@code refundedAmount} field — Stripe sends the cumulative amount, not
     * the delta, so we overwrite rather than add.
     */
    @Transactional
    public Optional<PaymentResponse> recordRefundByExternalId(
        String externalPaymentId,
        BigDecimal refundedAmountTotal,
        java.util.Map<String, Object> webhookSnapshot
    ) {
        log.info("Webhook recordRefundByExternalId: {} amount={}", externalPaymentId, refundedAmountTotal);
        return paymentRepository
            .findByExternalPaymentId(externalPaymentId)
            .map(payment -> {
                payment.setRefundedAmount(refundedAmountTotal);
                if (refundedAmountTotal.compareTo(payment.getAmount()) >= 0) {
                    payment.setStatus(PaymentStatus.REFUNDED);
                }
                mergeMetadata(payment, "webhook", webhookSnapshot);
                return mapToResponse(paymentRepository.save(payment));
            });
    }

    private void publishPaymentFailedEvent(Payment payment, Invoice invoice, String providerCode) {
        Tenant tenant = invoice.getTenant();
        PaymentFailedEvent event = PaymentFailedEvent.of(
            payment.getId(),
            invoice.getId(),
            invoice.getInvoiceNumber(),
            tenant.getId(),
            tenant.getName(),
            payment.getAmount(),
            payment.getCurrency() != null ? payment.getCurrency().getCode().name() : "USD",
            payment.getExternalPaymentId(),
            payment.getFailureReason(),
            providerCode
        );
        eventPublisher.publishEvent(event);
        log.debug("Published payment failed event for payment: {}", payment.getId());
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getInvoice().getId(),
            payment.getInvoice().getInvoiceNumber(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPaymentDate(),
            payment.getMethodType(),
            payment.getCurrency() != null ? payment.getCurrency().getCode().name() : null,
            payment.getExternalPaymentId(),
            payment.getFailureReason(),
            payment.getRefundedAmount(),
            payment.getCreatedBy(),
            payment.getCreatedDate(),
            payment.getLastModifiedBy(),
            payment.getLastModifiedDate()
        );
    }
}
