package com.humano.domain.billing;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.billing.InvoiceStatus;
import com.humano.domain.tenant.Tenant;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Invoice entity represents a billing document issued to a tenant for subscription charges.
 * <p>
 * This entity captures the financial transaction between the system and a tenant for
 * subscription services. It includes details such as amounts, tax calculations, payment
 * deadlines, and payment history. The invoice lifecycle is tracked through its status.
 * <ul>
 *   <li><b>invoiceNumber</b>: Unique identifier used for external reference and display.</li>
 *   <li><b>amount</b>: The subtotal amount before tax.</li>
 *   <li><b>taxAmount</b>: The calculated tax amount, if applicable.</li>
 *   <li><b>totalAmount</b>: The final amount including tax to be paid by the tenant.</li>
 *   <li><b>status</b>: Current state of the invoice (PENDING, PAID, OVERDUE, etc.)</li>
 *   <li><b>issueDate</b>: When the invoice was generated and issued to the tenant.</li>
 *   <li><b>dueDate</b>: The deadline by which payment must be made.</li>
 *   <li><b>paidDate</b>: When the invoice was successfully paid, if applicable.</li>
 * </ul>
 * <p>
 * The invoice is linked to a specific subscription and tenant, and can track multiple
 * payments made against it. It serves as the financial record for subscription billing.
 */
@Entity
@Table(name = "billing_invoice")
public class Invoice extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Unique invoice number used for external reference and display.
     * <p>
     * This is typically formatted as a sequential number with a prefix, e.g., "INV-001".
     * Must be unique across all invoices in the system.
     */
    @Column(name = "invoice_number", nullable = false, unique = true)
    @NotBlank(message = "Invoice number is required")
    @Size(min = 3, max = 50, message = "Invoice number must be between 3 and 50 characters")
    private String invoiceNumber;

    /**
     * The subtotal amount before tax.
     * <p>
     * This is the base amount charged for the subscription services.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Amount cannot be negative")
    private BigDecimal amount;

    /**
     * The calculated tax amount applied to this invoice.
     * <p>
     * Optional field as some jurisdictions may not require tax, or some services may be tax-exempt.
     */
    @Column(name = "tax_amount", precision = 19, scale = 4)
    @DecimalMin(value = "0.0", inclusive = true, message = "Tax amount cannot be negative")
    private BigDecimal taxAmount;

    /**
     * The final amount including tax to be paid by the tenant.
     * <p>
     * This is the sum of the base amount and tax amount.
     */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Total amount cannot be negative")
    private BigDecimal totalAmount;

    /**
     * Current status of the invoice.
     * <p>
     * Tracks the lifecycle of the invoice from creation to payment or write-off.
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Invoice status is required")
    private InvoiceStatus status;

    /**
     * The date when the invoice was generated and issued to the tenant.
     * <p>
     * Used to determine the start of the payment period.
     */
    @Column(name = "issue_date", nullable = false)
    @NotNull(message = "Issue date is required")
    private Instant issueDate;

    /**
     * The deadline by which payment must be made.
     * <p>
     * Used to determine when an invoice becomes overdue.
     */
    @Column(name = "due_date", nullable = false)
    @NotNull(message = "Due date is required")
    private Instant dueDate;

    /**
     * The date when the invoice was successfully paid, if applicable.
     * <p>
     * Null for unpaid or partially paid invoices.
     */
    @Column(name = "paid_date")
    private Instant paidDate;

    /**
     * The subscription this invoice is generated for.
     * <p>
     * Links to the Subscription entity to identify which service period is being billed.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    @NotNull(message = "Subscription is required")
    private Subscription subscription;

    /**
     * The tenant being billed.
     * <p>
     * Links to the Tenant entity to identify the customer receiving this invoice.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    @NotNull(message = "Tenant is required")
    private Tenant tenant;

    /**
     * Additional notes or comments about this invoice.
     * <p>
     * Can include special payment instructions, discount details, or other relevant information.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Collection of payments made against this invoice.
     * <p>
     * Tracks all payment transactions related to this invoice, allowing for partial payments.
     */
    @OneToMany(mappedBy = "invoice")
    private Set<Payment> payments = new HashSet<>();

    /**
     * Currency code for this invoice.
     * <p>
     * ISO 4217 currency code (e.g., "USD", "EUR") for all monetary amounts in this invoice.
     */
    @Column(name = "currency", length = 3, nullable = false)
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currency = "USD";


    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Instant getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(Instant issueDate) {
        this.issueDate = issueDate;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    public Instant getPaidDate() {
        return paidDate;
    }

    public void setPaidDate(Instant paidDate) {
        this.paidDate = paidDate;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Set<Payment> getPayments() {
        return payments;
    }

    public void setPayments(Set<Payment> payments) {
        this.payments = payments;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }


    public Invoice invoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
        return this;
    }

    public Invoice amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public Invoice taxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
        return this;
    }

    public Invoice totalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
        return this;
    }

    public Invoice status(InvoiceStatus status) {
        this.status = status;
        return this;
    }

    public Invoice issueDate(Instant issueDate) {
        this.issueDate = issueDate;
        return this;
    }

    public Invoice dueDate(Instant dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public Invoice paidDate(Instant paidDate) {
        this.paidDate = paidDate;
        return this;
    }

    public Invoice subscription(Subscription subscription) {
        this.subscription = subscription;
        return this;
    }

    public Invoice tenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }

    public Invoice notes(String notes) {
        this.notes = notes;
        return this;
    }

    public Invoice payments(Set<Payment> payments) {
        this.payments = payments;
        return this;
    }

    public Invoice currency(String currency) {
        this.currency = currency;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Invoice invoice = (Invoice) o;
        return Objects.equals(id, invoice.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Invoice{" +
            "id=" + id +
            ", invoiceNumber='" + invoiceNumber + '\'' +
            ", amount=" + amount +
            ", taxAmount=" + taxAmount +
            ", totalAmount=" + totalAmount +
            ", status=" + status +
            ", issueDate=" + issueDate +
            ", dueDate=" + dueDate +
            ", paidDate=" + paidDate +
            ", currency='" + currency + '\'' +
            '}';
    }
}
