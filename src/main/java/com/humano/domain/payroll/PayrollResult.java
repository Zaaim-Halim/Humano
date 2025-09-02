package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import com.humano.domain.hr.Employee;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * PayrollResult represents the aggregated payroll outcome for a single employee in a specific payroll run and period.
 * <p>
 * It stores the calculated gross pay, total deductions, net pay, and employer cost for the employee, as well as the currency used.
 * PayrollResult is linked to the PayrollRun, Employee, and Currency entities, and aggregates all PayrollLine items for the employee.
 * <ul>
 *   <li><b>gross</b>: The total earnings before deductions.</li>
 *   <li><b>totalDeductions</b>: The sum of all deductions (taxes, social contributions, etc.).</li>
 *   <li><b>net</b>: The net pay after deductions.</li>
 *   <li><b>employerCost</b>: The total cost to the employer, including net pay and employer charges.</li>
 *   <li><b>currency</b>: The currency in which payroll is calculated and paid.</li>
 *   <li><b>run</b>: The PayrollRun this result belongs to.</li>
 *   <li><b>employee</b>: The employee for whom this payroll result is calculated.</li>
 * </ul>
 * <p>
 * PayrollResult is essential for generating payslips, payroll reports, and supporting compliance and audit requirements.
 * It provides a clear summary of all payroll calculations for an employee in a given period.
 */
@Entity
@Table(name = "payroll_result")
public class PayrollResult extends AbstractAuditingEntity<UUID> {
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
     * The total earnings before deductions.
     * <p>
     * Represents the sum of all earnings including base salary, bonuses, and other additions.
     * Must be non-negative.
     */
    @Column(name = "gross", nullable = false)
    @NotNull(message = "Gross pay is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Gross pay cannot be negative")
    private BigDecimal gross = BigDecimal.ZERO;

    /**
     * The sum of all deductions (taxes, social contributions, etc.).
     * <p>
     * Represents the total amount deducted from gross pay.
     * Must be non-negative.
     */
    @Column(name = "total_deductions", nullable = false)
    @NotNull(message = "Total deductions is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Total deductions cannot be negative")
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    /**
     * The net pay after deductions.
     * <p>
     * Represents the amount actually paid to the employee (gross minus deductions).
     * Must be non-negative.
     */
    @Column(name = "net", nullable = false)
    @NotNull(message = "Net pay is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Net pay cannot be negative")
    private BigDecimal net = BigDecimal.ZERO;

    /**
     * The total cost to the employer, including net pay and employer charges.
     * <p>
     * Represents the full cost of employment beyond just the employee's pay.
     * Must be non-negative.
     */
    @Column(name = "employer_cost", nullable = false)
    @NotNull(message = "Employer cost is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Employer cost cannot be negative")
    private BigDecimal employerCost = BigDecimal.ZERO;

    /**
     * The PayrollRun this result belongs to.
     * <p>
     * Links to the payroll processing batch that generated this result.
     */
    @ManyToOne
    @JoinColumn(name = "run_id", nullable = false)
    @NotNull(message = "Payroll run is required")
    private PayrollRun run;

    /**
     * The employee for whom this payroll result is calculated.
     * <p>
     * Links to the employee receiving this compensation.
     */
    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    @NotNull(message = "Employee is required")
    private Employee employee;

    /**
     * The currency in which payroll is calculated and paid.
     * <p>
     * Specifies the currency for all monetary values in this result.
     */
    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    @NotNull(message = "Currency is required")
    private Currency currency;

    /**
     * The payroll period for which this result is calculated.
     * <p>
     * Links to the specific time period this payroll covers.
     */
    @ManyToOne
    @JoinColumn(name = "payroll_period_id", nullable = false)
    @NotNull(message = "Payroll period is required")
    private PayrollPeriod payrollPeriod;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public BigDecimal getGross() {
        return gross;
    }

    public PayrollResult gross(BigDecimal gross) {
        this.gross = gross;
        return this;
    }

    public void setGross(BigDecimal gross) {
        this.gross = gross;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public PayrollResult totalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
        return this;
    }

    public void setTotalDeductions(BigDecimal totalDeductions) {
        this.totalDeductions = totalDeductions;
    }

    public BigDecimal getNet() {
        return net;
    }

    public PayrollResult net(BigDecimal net) {
        this.net = net;
        return this;
    }

    public void setNet(BigDecimal net) {
        this.net = net;
    }

    public BigDecimal getEmployerCost() {
        return employerCost;
    }

    public PayrollResult employerCost(BigDecimal employerCost) {
        this.employerCost = employerCost;
        return this;
    }

    public void setEmployerCost(BigDecimal employerCost) {
        this.employerCost = employerCost;
    }

    public PayrollRun getRun() {
        return run;
    }

    public PayrollResult run(PayrollRun run) {
        this.run = run;
        return this;
    }

    public void setRun(PayrollRun run) {
        this.run = run;
    }

    public Employee getEmployee() {
        return employee;
    }

    public PayrollResult employee(Employee employee) {
        this.employee = employee;
        return this;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Currency getCurrency() {
        return currency;
    }

    public PayrollResult currency(Currency currency) {
        this.currency = currency;
        return this;
    }

    public void setCurrency(Currency currency) {
        this.currency = currency;
    }

    public PayrollPeriod getPayrollPeriod() {
        return payrollPeriod;
    }

    public PayrollResult payrollPeriod(PayrollPeriod payrollPeriod) {
        this.payrollPeriod = payrollPeriod;
        return this;
    }

    public void setPayrollPeriod(PayrollPeriod payrollPeriod) {
        this.payrollPeriod = payrollPeriod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PayrollResult that = (PayrollResult) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PayrollResult{" +
            "id=" + id +
            ", gross=" + gross +
            ", totalDeductions=" + totalDeductions +
            ", net=" + net +
            ", employerCost=" + employerCost +
            ", currency=" + (currency != null ? currency.getCode() : null) +
            '}';
    }
}
