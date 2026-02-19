package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * Represents an expense claim submitted by an employee for reimbursement.
 * <p>
 * Includes claim date, amount, description, status, receipt, and the related employee.
 */
@Entity
@Table(name = "expense_claim")
public class ExpenseClaim extends AbstractAuditingEntity<UUID> {

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
     * Date the claim was made.
     */
    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;

    /**
     * Amount claimed for reimbursement.
     */
    @Column(name = "amount", nullable = false)
    @Min(0)
    private BigDecimal amount;

    /**
     * Optional description of the claim.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Status of the claim (e.g., PENDING, APPROVED, REJECTED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExpenseClaimStatus status;

    /**
     * Optional URL to the uploaded receipt.
     */
    @Column(name = "receipt_url")
    private String receiptUrl; // optional link to uploaded receipt

    /**
     * The employee who submitted the claim.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Getters and setters
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getClaimDate() {
        return claimDate;
    }

    public ExpenseClaim claimDate(LocalDate claimDate) {
        this.claimDate = claimDate;
        return this;
    }

    public void setClaimDate(LocalDate claimDate) {
        this.claimDate = claimDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExpenseClaim amount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public ExpenseClaim description(String description) {
        this.description = description;
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ExpenseClaimStatus getStatus() {
        return status;
    }

    public ExpenseClaim status(ExpenseClaimStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(ExpenseClaimStatus status) {
        this.status = status;
    }

    public String getReceiptUrl() {
        return receiptUrl;
    }

    public ExpenseClaim receiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
        return this;
    }

    public void setReceiptUrl(String receiptUrl) {
        this.receiptUrl = receiptUrl;
    }

    public Employee getEmployee() {
        return employee;
    }

    public ExpenseClaim employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpenseClaim that = (ExpenseClaim) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ExpenseClaim{" + "id=" + id + ", claimDate=" + claimDate + ", amount=" + amount + ", status=" + status + '}';
    }
}
