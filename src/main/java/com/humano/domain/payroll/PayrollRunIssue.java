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
 * An employee a payroll run's latest calculation could not pay: refused by a payroll template,
 * missing template details, or any other calculation error. The run has no result for them, so
 * approving it leaves them unpaid; kept with the run (replaced on every calculation) so the run
 * page shows them after a reload and approval is blocked until they are resolved or overridden.
 */
@Entity
@Table(name = "payroll_run_issue")
public class PayrollRunIssue extends AbstractAuditingEntity<UUID> {

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

    /** {@code TEMPLATE_PROFILE_INCOMPLETE} or {@code CALCULATION_ERROR}. */
    @Column(name = "error_code", nullable = false, length = 60)
    private String errorCode;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** The template whose details are missing, for {@code TEMPLATE_PROFILE_INCOMPLETE}; null otherwise. */
    @Column(name = "template_id", length = 80)
    private String templateId;

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
}
