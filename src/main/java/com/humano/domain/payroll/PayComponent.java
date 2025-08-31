package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author halimzaaim
 * example:
 * 2. Examples of PayComponents
 * /**
 * | Code             | Kind            | Measure | Notes                                 |
 * |------------------|-----------------|---------|---------------------------------------|
 * | BASIC            | EARNING         | AMOUNT  | Fixed base salary                     |
 * | OT               | EARNING         | HOURS   | Overtime hours × rate                 |
 * | BONUS            | EARNING         | AMOUNT  | One-time bonus                        |
 * | LEAVE_DEDUCTION  | DEDUCTION       | AMOUNT  | Unpaid leave deduction                |
 * | TAX_PIT          | DEDUCTION       | RATE    | Personal income tax deduction         |
 * | HEALTH_INSURANCE | EMPLOYER_CHARGE | AMOUNT  | Employer contribution for benefits    |
 */


@Entity
@Table(name = "pay_component")
public class PayComponent extends AbstractAuditingEntity<UUID> {
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

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true)
    private PayComponentCode code; // BASIC, OT, TAX_PIT, etc.

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private Kind kind;  // EARNING, DEDUCTION, EMPLOYER_CHARGE

    @Enumerated(EnumType.STRING)
    @Column(name = "measure", nullable = false)
    private Measurement measure; // AMOUNT, RATE, HOURS

    @Column(name = "taxable", nullable = false)
    private Boolean taxable;

    @Column(name = "contributes_to_social", nullable = false)
    private Boolean contributesToSocial;

    @Column(name = "percentage", nullable = false)
    private Boolean percentage; // true if % based

    @Column(name = "calc_phase")
    private Integer calcPhase; // Stage of calculation

    @OneToMany(mappedBy = "payComponent", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PayRule> payRules = new HashSet<>();

    @Override
    public UUID getId() {
        return id;
    }
}
