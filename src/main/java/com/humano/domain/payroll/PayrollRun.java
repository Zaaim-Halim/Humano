package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.hr.Employee;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.time.OffsetDateTime;
import java.util.UUID;

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
        parameters = {
            @Parameter(
                name = "uuid_gen_strategy_class",
                value = "org.hibernate.id.uuid.CustomVersionOneStrategy"
            )
        }
    )
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scope", nullable = false)
    private String scope; // ALL, UNIT:<id>, EMPLOYEE:<id>

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatus status;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "hash", unique = true)
    private String hash; // idempotency marker

    @ManyToOne
    @JoinColumn(name = "period_id", nullable = false)
    private PayrollPeriod period;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private Employee approvedBy;

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
