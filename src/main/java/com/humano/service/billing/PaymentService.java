package com.humano.service.billing;

import com.humano.domain.Currency;
import com.humano.domain.billing.Invoice;
import com.humano.domain.billing.Payment;
import com.humano.domain.billing.Subscription;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.enumeration.billing.PaymentStatus;
import com.humano.domain.enumeration.billing.SubscriptionStatus;
import com.humano.domain.enumeration.tenant.TenantStatus;
import com.humano.domain.tenant.Tenant;
import com.humano.events.PaymentCompletedEvent;
import com.humano.repository.CurrencyRepository;
import com.humano.repository.billing.InvoiceRepository;
import com.humano.repository.billing.PaymentRepository;
import com.humano.repository.billing.SubscriptionRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.billing.dto.requests.CreatePaymentRequest;
import com.humano.service.billing.dto.responses.PaymentResponse;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final CurrencyRepository currencyRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(
        PaymentRepository paymentRepository,
        InvoiceRepository invoiceRepository,
        CurrencyRepository currencyRepository,
        SubscriptionRepository subscriptionRepository,
        TenantRepository tenantRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.currencyRepository = currencyRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.eventPublisher = eventPublisher;
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

        Currency currency = currencyRepository
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
            payment.getCurrency() != null ? payment.getCurrency().getCode() : "USD",
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

                // TODO: Integrate with payment provider to process actual refund

                log.info("Processed refund of {} for payment: {}", actualRefundAmount, id);
                return mapToResponse(paymentRepository.save(payment));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Payment", id));
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

                // TODO: Integrate with payment provider to process payment with new token
                // For now, simulate success
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setPaymentDate(Instant.now());
                payment.setExternalPaymentId("retry_" + UUID.randomUUID().toString().substring(0, 8));
                payment.setFailureReason(null);

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

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getInvoice().getId(),
            payment.getInvoice().getInvoiceNumber(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getPaymentDate(),
            payment.getMethodType(),
            payment.getCurrency() != null ? payment.getCurrency().getCode() : null,
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
