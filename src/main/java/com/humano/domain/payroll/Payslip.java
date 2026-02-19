package com.humano.domain.payroll;

import com.humano.domain.shared.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Human-readable payslip number (e.g., "PS-2025-08-001").
     * <p>
     * Provides a user-friendly identifier for the payslip.
     */
    @Column(name = "number", nullable = false, unique = true)
    @NotNull(message = "Payslip number is required")
    @Size(min = 3, max = 50, message = "Payslip number must be between 3 and 50 characters")
    private String number;

    /**
     * URL to the generated payslip PDF artifact.
     * <p>
     * Provides access to the downloadable payslip document.
     */
    @Column(name = "pdf_url")
    @Size(max = 255, message = "PDF URL cannot exceed 255 characters")
    private String pdfUrl;

    /**
     * The PayrollResult containing all payroll calculations for the employee and period.
     * <p>
     * Links to the detailed calculation results that form the basis of this payslip.
     */
    @OneToOne
    @JoinColumn(name = "result_id", nullable = false, unique = true)
    private PayrollResult result;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public Payslip number(String number) {
        this.number = number;
        return this;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public Payslip pdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
        return this;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public PayrollResult getResult() {
        return result;
    }

    public Payslip result(PayrollResult result) {
        this.result = result;
        return this;
    }

    public void setResult(PayrollResult result) {
        this.result = result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payslip payslip = (Payslip) o;
        return Objects.equals(id, payslip.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Payslip{" + "id=" + id + ", number='" + number + '\'' + ", pdfUrl='" + pdfUrl + '\'' + '}';
    }
}
