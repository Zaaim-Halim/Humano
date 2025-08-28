package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
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
    private BigDecimal amount;

    /**
     * Optional description of the claim.
     */
    @Column(name = "description")
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
    public UUID getId() {
        return id;
    }

}
