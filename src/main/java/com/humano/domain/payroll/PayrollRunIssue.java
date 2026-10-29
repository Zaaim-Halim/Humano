package com.humano.domain.payroll;

import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Something a payroll run's latest calculation found about an employee, kept with the run (replaced
 * on every calculation) so the run page shows it after a reload.
 * <p>
 * An {@link #ERROR}: the calculation could not pay them (refused by a payroll template, missing
 * template details, any other calculation error). The run has no result for them, so approving it
 * leaves them unpaid, and approval is blocked until they are resolved or overridden.
 * <p>
 * A {@link #WARNING}: they are paid, but something needs checking, e.g. leave paid in full because no
 * leave pay rule covers it. Approval is not blocked.
 */
@Entity
@Table(name = "payroll_run_issue")
public class PayrollRunIssue extends AbstractAuditingEntity<UUID> {

    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private PayrollRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** {@code TEMPLATE_PROFILE_INCOMPLETE} or {@code CALCULATION_ERROR}; {@code LEAVE_WITHOUT_RULE} for a warning. */
    @Column(name = "error_code", nullable = false, length = 60)
    private String errorCode;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** The template whose details are missing, for {@code TEMPLATE_PROFILE_INCOMPLETE}; null otherwise. */
    @Column(name = "template_id", length = 80)
    private String templateId;

    /** {@link #ERROR} or {@link #WARNING}. */
    @Column(name = "severity", nullable = false, length = 10)
    private String severity = ERROR;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public PayrollRun getRun() {
        return run;
    }

    public void setRun(PayrollRun run) {
        this.run = run;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
