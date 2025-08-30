package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.UUID;

/**
 * Payslip represents the persisted payroll artifact for an employee for a specific payroll period and run.
 * <p>
 * It links to the PayrollResult, which contains all calculated payroll data (gross, net, deductions, employer cost, etc.),
 * and provides a human-readable payslip number and a URL to the generated PDF artifact. Payslip is the final output
 * of the payroll process, used for employee communication, compliance, and record-keeping.
 * <ul>
 *   <li><b>result</b>: The PayrollResult containing all payroll calculations for the employee and period.</li>
 *   <li><b>number</b>: Human-readable payslip number (e.g., "PS-2025-08-001").</li>
 *   <li><b>pdfUrl</b>: URL to the generated payslip PDF artifact.</li>
 * </ul>
 * <p>
 * Payslip is essential for providing employees with detailed payroll information, supporting legal and business
 * requirements for payroll documentation, and enabling digital access to payroll records.
 */
@Entity
@Table(name = "payslip")
public class Payslip extends AbstractAuditingEntity<UUID> {
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

    @Column(name = "number", nullable = false, unique = true)
    private String number; // human-readable : "PS-2025-08-001"

    @Column(name = "pdf_url")
    private String pdfUrl; // generated artifact

    @OneToOne
    @JoinColumn(name = "result_id", nullable = false, unique = true)
    private PayrollResult result;

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
