package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.PositionChangeStatus;
import com.humano.domain.enumeration.hr.PositionChangeType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "employee_position_history")
public class EmployeePositionHistory extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "old_salary")
    private BigDecimal oldSalary;
    @Column(name = "new_salary")

    private BigDecimal newSalary;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type")
    private PositionChangeType changeType; // PROMOTION, DEMOTION, TRANSFER, etc.

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PositionChangeStatus status; // PENDING, APPLIED, CANCELLED

    @Column(name = "reason")
    private String reason;

    @ManyToOne
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "old_position_id")
    private Position oldPosition;

    @ManyToOne
    @JoinColumn(name = "new_position_id")
    private Position newPosition;

    @ManyToOne
    @JoinColumn(name = "old_unit_id")
    private OrganizationalUnit oldUnit;

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
