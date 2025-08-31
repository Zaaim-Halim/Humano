package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Country;
import com.humano.domain.enumeration.hr.LeaveType;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author halimzaaim
 */
@Entity
@Table(name = "leave_type_rule",
    uniqueConstraints = @UniqueConstraint(columnNames = {"leave_type", "country_id"}))
public class LeaveTypeRule extends AbstractAuditingEntity<UUID> {
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;  // country-specific rules

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal deductionPercentage; // 0.0 - 100.0

    @Column(nullable = false)
    private Boolean affectsTaxableSalary = false; // optional, if leave affects gross salary

    @Override
    public UUID getId() {
        return null;
    }
}
