package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import com.humano.domain.shared.AbstractAuditingEntity;
import com.humano.domain.shared.Employee;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

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
    @GenericGenerator(
        name = "UUID",
        strategy = "org.hibernate.id.UUIDGenerator",
        parameters = { @Parameter(name = "uuid_gen_strategy_class", value = "org.hibernate.id.uuid.CustomVersionOneStrategy") }
    )
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
    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getProviderName() {
        return providerName;
    }

    public HealthInsurance providerName(String providerName) {
        this.providerName = providerName;
        return this;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public HealthInsurance policyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
        return this;
    }

    public void setPolicyNumber(String policyNumber) {
        this.policyNumber = policyNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public HealthInsurance startDate(LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public HealthInsurance endDate(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getCoverageAmount() {
        return coverageAmount;
    }

    public HealthInsurance coverageAmount(BigDecimal coverageAmount) {
        this.coverageAmount = coverageAmount;
        return this;
    }

    public void setCoverageAmount(BigDecimal coverageAmount) {
        this.coverageAmount = coverageAmount;
    }

    public HealthInsuranceStatus getStatus() {
        return status;
    }

    public HealthInsurance status(HealthInsuranceStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(HealthInsuranceStatus status) {
        this.status = status;
    }

    public Employee getEmployee() {
        return employee;
    }

    public HealthInsurance employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HealthInsurance that = (HealthInsurance) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return (
            "HealthInsurance{" +
            "id=" +
            id +
            ", providerName='" +
            providerName +
            '\'' +
            ", policyNumber='" +
            policyNumber +
            '\'' +
            ", startDate=" +
            startDate +
            ", endDate=" +
            endDate +
            ", status=" +
            status +
            '}'
        );
    }
}
