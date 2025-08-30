package com.humano.domain.payroll;

import com.humano.domain.AbstractAuditingEntity;
import com.humano.domain.Currency;
import com.humano.domain.hr.Employee;
import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
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
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "gross", nullable = false)
    private BigDecimal gross;

    @Column(name = "total_deductions", nullable = false)
    private BigDecimal totalDeductions;

    @Column(name = "net", nullable = false)
    private BigDecimal net;

    @Column(name = "employer_cost", nullable = false)
    private BigDecimal employerCost;

    @ManyToOne
    @JoinColumn(name = "run_id", nullable = false)
    private PayrollRun run;

    @ManyToOne
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    @ManyToOne
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriod payrollPeriod;

    @Override
    public UUID getId() {
        return id;
    }
    // Getters and setters can be added as needed
}
