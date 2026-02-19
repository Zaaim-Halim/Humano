package com.humano.domain.payroll;

import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

/**
 * PayrollRun represents a single execution of payroll processing for a given payroll period and scope.
 * <p>
 * It tracks the payroll period, scope (e.g., all employees, a specific unit, or a single employee),
 * status of the run (draft, calculated, approved, posted), approval details, and an idempotency hash.
 * PayrollRun is the central entity for managing payroll calculations, approvals, and posting events.
 * <ul>
 *   <li><b>period</b>: The PayrollPeriod for which payroll is being processed.</li>
 *   <li><b>scope</b>: The scope of the run (ALL, UNIT:&lt;id&gt;, EMPLOYEE:&lt;id&gt;).</li>
 *   <li><b>status</b>: The current status of the payroll run (DRAFT, CALCULATED, APPROVED, POSTED).</li>
 *   <li><b>approvedAt</b>: The timestamp when the payroll run was approved.</li>
 *   <li><b>approvedBy</b>: The Employee who approved the payroll run.</li>
 *   <li><b>hash</b>: An idempotency marker to prevent duplicate processing.</li>
 * </ul>
 * <p>
 * PayrollRun is essential for orchestrating payroll calculations, tracking approval workflows, and ensuring
 * that payroll is processed and posted correctly for each period and scope.
 */
@Entity
@Table(name = "payroll_run")
public class PayrollRun extends AbstractAuditingEntity<UUID> {

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
     * The scope of the run (ALL, UNIT:<id>, EMPLOYEE:<id>).
     * <p>
     * Defines which employees are included in this payroll run.
     */
    @Column(name = "scope", nullable = false)
    @NotNull(message = "Scope is required")
    @Size(min = 3, max = 100, message = "Scope must be between 3 and 100 characters")
    private String scope;

    /**
     * The current status of the payroll run.
     * <p>
     * Tracks the stage of the payroll process (DRAFT, CALCULATED, APPROVED, POSTED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull(message = "Status is required")
    private RunStatus status;

    /**
     * The timestamp when the payroll run was approved.
     * <p>
     * Records when the run was officially approved for processing.
     */
    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    /**
     * An idempotency marker to prevent duplicate processing.
     * <p>
     * Used to ensure that payroll calculations are not accidentally duplicated.
     */
    @Column(name = "hash", unique = true)
    @Size(max = 100, message = "Hash cannot exceed 100 characters")
    private String hash;

    /**
     * The PayrollPeriod for which payroll is being processed.
     * <p>
     * Defines the time period (e.g., month) for which pay is being calculated.
     */
    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private PayrollPeriod period;

    /**
     * The Employee who approved the payroll run.
     * <p>
     * Records who authorized the payroll processing.
     */
    @ManyToOne
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getScope() {
        return scope;
    }

    public PayrollRun scope(String scope) {
        this.scope = scope;
        return this;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public RunStatus getStatus() {
        return status;
    }

    public PayrollRun status(RunStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public PayrollRun approvedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
        return this;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public String getHash() {
        return hash;
    }

    public PayrollRun hash(String hash) {
        this.hash = hash;
        return this;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public PayrollPeriod getPeriod() {
        return period;
    }

    public PayrollRun period(PayrollPeriod period) {
        this.period = period;
        return this;
    }

    public void setPeriod(PayrollPeriod period) {
        this.period = period;
    }

    public Employee getApprovedBy() {
        return approvedBy;
    }

    public PayrollRun approvedBy(Employee approvedBy) {
        this.approvedBy = approvedBy;
        return this;
    }

    public void setApprovedBy(Employee approvedBy) {
        this.approvedBy = approvedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayrollRun that = (PayrollRun) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "PayrollRun{" +
            "id=" +
            id +
            ", scope='" +
            scope +
            '\'' +
            ", status=" +
            status +
            ", approvedAt=" +
            approvedAt +
            ", hash='" +
            hash +
            '\'' +
            '}'
        );
    }
}
