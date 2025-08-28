package com.humano.domain.hr;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a health insurance policy for an employee, including provider, policy number, coverage, and status.
 * <p>
 * Used to track employee insurance benefits and coverage details.
 */
@Entity
@Table(name = "health_insurance")
public class HealthInsurance extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Name of the insurance provider.
     */
    @Column(name = "provider_name", nullable = false)
    private String providerName;

    /**
     * Policy number of the insurance.
     */
    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    /**
     * Start date of the insurance coverage.
     */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * End date of the insurance coverage.
     */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Amount of coverage provided by the policy.
     */
    @Column(name = "coverage_amount", nullable = false)
    private BigDecimal coverageAmount;

    /**
     * Status of the insurance policy (e.g., ACTIVE, EXPIRED).
     */
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private HealthInsuranceStatus status;

    /**
     * The employee covered by this insurance policy.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Getters and setters
    public UUID getId() {
        return id;
    }

}
