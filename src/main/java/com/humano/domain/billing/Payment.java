package com.humano.domain.billing;

import com.humano.domain.enumeration.billing.PaymentMethodType;
import com.humano.domain.enumeration.billing.PaymentStatus;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Payment entity represents a financial transaction for an invoice.
 * <p>
 * This entity tracks the transaction details when a tenant pays an invoice,
 * including the amount, method, status, and timing of the payment. It supports
 * various payment methods and can track refunds and failures.
 * <ul>
 *   <li><b>amount</b>: The payment amount received from the tenant.</li>
 *   <li><b>status</b>: Current state of the payment (PENDING, COMPLETED, FAILED, REFUNDED).</li>
 *   <li><b>paymentDate</b>: When the payment was processed.</li>
 *   <li><b>invoice</b>: The invoice this payment is applied to.</li>
 *   <li><b>methodType</b>: The payment method used (CARD, BANK_TRANSFER, etc.).</li>
 *   <li><b>currency</b>: The currency of the payment amount.</li>
 *   <li><b>externalPaymentId</b>: Reference ID from the payment processor.</li>
 *   <li><b>failureReason</b>: Description of why the payment failed, if applicable.</li>
 *   <li><b>refundedAmount</b>: Amount refunded from this payment, if any.</li>
 * </ul>
 * <p>
 * Payments are associated with invoices to track the financial relationship
 * between tenants and their subscription charges.
 */
@Entity
@Table(name = "billing_payment")
public class Payment extends AbstractAuditingEntity<UUID> {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The payment amount received from the tenant.
     * <p>
     * This represents the actual money transferred in the payment transaction.
     * It may be less than, equal to, or more than the invoice total amount.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Payment amount must be positive")
    private BigDecimal amount;

    /**
     * Current state of the payment.
     * <p>
     * Tracks the lifecycle of the payment transaction (PENDING, COMPLETED, FAILED, REFUNDED).
     * This status determines whether the payment has successfully cleared and been applied to the invoice.
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Payment status is required")
    private PaymentStatus status;

    /**
     * When the payment was processed.
     * <p>
     * Records the timestamp when the payment transaction occurred.
     * Used for reconciliation and reporting.
     */
    @Column(name = "payment_date", nullable = false)
    @NotNull(message = "Payment date is required")
    private Instant paymentDate;

    /**
     * The invoice this payment is applied to.
     * <p>
     * Links to the Invoice entity to track which bill this payment is addressing.
     * Multiple payments may be linked to a single invoice (partial payments).
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    @NotNull(message = "Invoice is required")
    private Invoice invoice;

    /**
     * The payment method used.
     * <p>
     * Specifies how the payment was made (CARD, BANK_TRANSFER, PAYPAL, WIRE).
     * This affects processing times and available operations (like refunds).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    @NotNull(message = "Payment method is required")
    private PaymentMethodType methodType = PaymentMethodType.CARD;

    /**
     * Reference ID from the payment processor.
     * <p>
     * A unique identifier from the external payment system (e.g., Stripe charge ID).
     * Used for reconciliation and cross-referencing with payment gateway records.
     */
    @Column(name = "external_payment_id")
    @Size(max = 255, message = "External payment ID cannot exceed 255 characters")
    private String externalPaymentId;

    /**
     * Description of why the payment failed.
     * <p>
     * For failed payments, provides details about the reason for failure
     * (e.g., "Insufficient funds", "Card expired").
     */
    @Column(name = "failure_reason")
    @Size(max = 255, message = "Failure reason cannot exceed 255 characters")
    private String failureReason;

    /**
     * Amount refunded from this payment.
     * <p>
     * Tracks any money returned to the tenant from this payment.
     * May be partial or equal to the original amount.
     */
    @Column(name = "refunded_amount", precision = 19, scale = 4)
    @DecimalMin(value = "0.0", inclusive = true, message = "Refunded amount cannot be negative")
    private BigDecimal refundedAmount;

    /**
     * Number of retry attempts for failed payments.
     * <p>
     * For automated payment recovery, tracks how many times the system
     * has attempted to process the payment after initial failure.
     */
    @Column(name = "retry_count")
    private Integer retryCount = 0;

    /**
     * When the next retry attempt is scheduled.
     * <p>
     * For failed payments in retry flow, indicates when the system
     * will next attempt to process the payment.
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    /**
     * When the payment was captured (for auth/capture flows).
     * <p>
     * For two-step payment processing, records when funds were actually
     * captured after initial authorization.
     */
    @Column(name = "captured_at")
    private Instant capturedAt;

    /**
     * The currency of the payment amount.
     * <p>
     * Specifies which currency the payment was made in.
     * Links to the Currency entity to identify the monetary unit.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "currency_id", nullable = false)
    @NotNull(message = "Currency is required")
    private Currency currency;

    /**
     * Optimistic locking version for concurrent modifications.
     */
    @Version
    private Long version;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Payment amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Payment status(PaymentStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public Instant getPaymentDate() {
        return paymentDate;
    }

    public Payment paymentDate(Instant paymentDate) {
        this.paymentDate = paymentDate;
        return this;
    }

    public void setPaymentDate(Instant paymentDate) {
        this.paymentDate = paymentDate;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public Payment invoice(Invoice invoice) {
        this.invoice = invoice;
        return this;
    }

    public void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }

    public PaymentMethodType getMethodType() {
        return methodType;
    }

    public Payment methodType(PaymentMethodType methodType) {
        this.methodType = methodType;
        return this;
    }

    public void setMethodType(PaymentMethodType methodType) {
        this.methodType = methodType;
    }

    /**
     * Legacy getter for method. Returns methodType name.
     * @return The payment method as a String
     */
    public String getMethod() {
        return methodType != null ? methodType.name() : null;
    }

    /**
     * Legacy setter for method. Converts String to methodType enum.
     * @param method The payment method as a String
     */
    public void setMethod(String method) {
        if (method != null) {
            this.methodType = PaymentMethodType.valueOf(method);
        }
    }

    public String getExternalPaymentId() {
        return externalPaymentId;
    }

    public Payment externalPaymentId(String externalPaymentId) {
        this.externalPaymentId = externalPaymentId;
        return this;
    }

    public void setExternalPaymentId(String externalPaymentId) {
        this.externalPaymentId = externalPaymentId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Payment failureReason(String failureReason) {
        this.failureReason = failureReason;
        return this;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public BigDecimal getRefundedAmount() {
        return refundedAmount;
    }

    public Payment refundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
        return this;
    }

    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public Payment retryCount(Integer retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Payment nextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
        return this;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public Payment capturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
        return this;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Currency getCurrency() {
        return currency;
    }

    public Payment currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public Long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(id, payment.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "Payment{" +
            "id=" +
            id +
            ", amount=" +
            amount +
            ", status=" +
            status +
            ", paymentDate=" +
            paymentDate +
            ", methodType=" +
            methodType +
            ", externalPaymentId='" +
            externalPaymentId +
            '\'' +
            ", refundedAmount=" +
            refundedAmount +
            '}'
        );
    }
}
