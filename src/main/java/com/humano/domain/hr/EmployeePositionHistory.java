package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks changes in an employee's position, salary, and organizational unit over time.
 * <p>
 * Used for auditing promotions, transfers, and other position changes.
 */
@Entity
@Table(name = "employee_position_history")
public class EmployeePositionHistory extends AbstractAuditingEntity<UUID> {
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
     * Previous salary before the change.
     */
    @Column(name = "old_salary")
    private BigDecimal oldSalary;
    /**
     * New salary after the change.
     */
    @Column(name = "new_salary")

    private BigDecimal newSalary;
    /**
     * Date the change takes effect.
     */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;
    /**
     * Type of position change (e.g., PROMOTION, TRANSFER).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private PositionChangeType changeType;
    /**
     * Status of the change (e.g., PENDING, APPLIED).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PositionChangeStatus status; // PENDING, APPLIED, CANCELLED
    /**
     * Reason for the change.
     */
    @Column(name = "reason")
    private String reason;
    /**
     * The employee affected by the change.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;
    /**
     * Previous position before the change.
     */
    @ManyToOne
    @JoinColumn(name = "old_position_id")
    private Position oldPosition;
    /**
     * New position after the change.
     */
    @ManyToOne
    @JoinColumn(name = "new_position_id")
    private Position newPosition;
    /**
     * Previous organizational unit before the change.
     */
    @ManyToOne
    @JoinColumn(name = "old_unit_id")
    private OrganizationalUnit oldUnit;
    /**
     * New organizational unit after the change.
     */
    @ManyToOne
    @JoinColumn(name = "new_unit_id")
    private OrganizationalUnit newUnit;

    @ManyToOne
    @JoinColumn(name = "old_manager_id")
    private Employee oldManager;

    @ManyToOne
    @JoinColumn(name = "new_manager_id")
    private Employee newManager;

    @Override
    public UUID getId() {
        return id;
    }

    // Getters and setters can be added here if needed
}
