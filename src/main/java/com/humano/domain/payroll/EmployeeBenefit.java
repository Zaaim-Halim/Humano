package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.BenefitStatus;
import com.humano.domain.enumeration.payroll.BenefitType;
import com.humano.domain.hr.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * EmployeeBenefit entity represents benefits provided to employees and their associated costs.
 * <p>
 * This tracks different types of benefits (health insurance, retirement plans, etc.),
 * their costs to both employer and employee, and their effective dates.
 * <p>
 * Employee benefits are an important component of total compensation that extend beyond
 * direct salary. They include health insurance, retirement plans, and other non-cash
 * components that add value to the employment relationship. Managing benefits effectively
 * is crucial for both employee satisfaction and controlling company expenses.
 */
@Entity
@Table(name = "employee_benefit")
public class EmployeeBenefit extends AbstractAuditingEntity<UUID> {

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
     * The employee receiving the benefit.
     * <p>
     * Links to the Employee entity in the HR domain to maintain
     * relationship between benefits and employees.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /**
     * The type of benefit (HEALTH_INSURANCE, RETIREMENT_PLAN, etc.).
     * <p>
     * Categorizes the benefit for reporting, processing, and compliance purposes.
     * Different benefit types have different tax implications and regulatory requirements.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private BenefitType type;

    /**
     * The cost to the employer for providing this benefit.
     * <p>
     * Represents what the company pays for this benefit per pay period or month.
     * Used for tracking total compensation costs and benefit expense management.
     * <p>
     * Minimum value is 0, as costs cannot be negative.
     */
    @Column(name = "employer_cost")
    @DecimalMin(value = "0.0", inclusive = true, message = "Employer cost cannot be negative")
    private BigDecimal employerCost = BigDecimal.ZERO;

    /**
     * The cost to the employee for this benefit (deducted from pay).
     * <p>
     * Represents the employee's contribution toward the benefit cost.
     * Usually deducted from the employee's paycheck on a pre-tax or post-tax basis.
     * <p>
     * Minimum value is 0, as costs cannot be negative.
     */
    @Column(name = "employee_cost")
    @DecimalMin(value = "0.0", inclusive = true, message = "Employee cost cannot be negative")
    private BigDecimal employeeCost = BigDecimal.ZERO;

    /**
     * The date from which this benefit becomes effective.
     * <p>
     * Used to track when coverage begins and when the benefit should
     * start being included in payroll and benefit calculations.
     */
    @Column(name = "effective_from", nullable = false)
    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    /**
     * The date until which this benefit is effective.
     * <p>
     * Optional field used to track when coverage ends or when the benefit
     * should no longer be applied. If null, the benefit is considered
     * ongoing until explicitly terminated.
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * The current status of the benefit enrollment.
     * <p>
     * Tracks whether the benefit is active, pending, terminated, etc.
     * Different statuses affect eligibility, billing, and coverage.
     * <p>
     * Default is PENDING until explicitly activated.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @NotNull(message = "Benefit status is required")
    private BenefitStatus status = BenefitStatus.PENDING;

    /**
     * The name of the benefit plan.
     * <p>
     * Identifies the specific plan or tier within a benefit type.
     * For example, "Gold PPO" for health insurance or "401(k) Traditional" for retirement.
     */
    @Column(name = "plan_name", nullable = false)
    @NotNull(message = "Plan name is required")
    @Size(min = 2, max = 100, message = "Plan name must be between 2 and 100 characters")
    private String planName;

    /**
     * Additional details about the benefit plan.
     * <p>
     * Provides more comprehensive information about coverage, terms,
     * conditions, or specific features of the benefit plan.
     */
    @Column(name = "plan_details", columnDefinition = "TEXT")
    @Size(max = 4000, message = "Plan details cannot exceed 4000 characters")
    private String planDetails;

    /**
     * The currency of the benefit costs.
     * <p>
     * Links to the Currency entity to specify the currency in which
     * the employer and employee costs are denominated.
     */
    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public EmployeeBenefit employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public BenefitType getType() {
        return type;
    }

    public EmployeeBenefit type(BenefitType type) {
        this.type = type;
        return this;
    }

    public void setType(BenefitType type) {
        this.type = type;
    }

    public BigDecimal getEmployerCost() {
        return employerCost;
    }

    public EmployeeBenefit employerCost(BigDecimal employerCost) {
        this.employerCost = employerCost;
        return this;
    }

    public void setEmployerCost(BigDecimal employerCost) {
        this.employerCost = employerCost;
    }

    public BigDecimal getEmployeeCost() {
        return employeeCost;
    }

    public EmployeeBenefit employeeCost(BigDecimal employeeCost) {
        this.employeeCost = employeeCost;
        return this;
    }

    public void setEmployeeCost(BigDecimal employeeCost) {
        this.employeeCost = employeeCost;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public EmployeeBenefit effectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
        return this;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public EmployeeBenefit effectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
        return this;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public BenefitStatus getStatus() {
        return status;
    }

    public EmployeeBenefit status(BenefitStatus status) {
        this.status = status;
        return this;
    }

    public void setStatus(BenefitStatus status) {
        this.status = status;
    }

    public String getPlanName() {
        return planName;
    }

    public EmployeeBenefit planName(String planName) {
        this.planName = planName;
        return this;
    }

    public void setPlanName(String planName) {
        this.planName = planName;
    }

    public String getPlanDetails() {
        return planDetails;
    }

    public EmployeeBenefit planDetails(String planDetails) {
        this.planDetails = planDetails;
        return this;
    }

    public void setPlanDetails(String planDetails) {
        this.planDetails = planDetails;
    }

    public Currency getCurrency() {
        return currency;
    }

    public EmployeeBenefit currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmployeeBenefit that = (EmployeeBenefit) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EmployeeBenefit{" +
            "id=" + id +
            ", type=" + type +
            ", employerCost=" + employerCost +
            ", employeeCost=" + employeeCost +
            ", effectiveFrom=" + effectiveFrom +
            ", effectiveTo=" + effectiveTo +
            ", status=" + status +
            ", planName='" + planName + '\'' +
            '}';
    }
}
