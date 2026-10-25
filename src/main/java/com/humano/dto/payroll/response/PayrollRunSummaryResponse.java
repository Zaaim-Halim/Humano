package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for payroll run summary with aggregated statistics.
 * <p>
 * When the run's employees are paid in several currencies and it has no reporting currency, the
 * single totals, {@code currencyCode} and {@code comparison} are null, {@code topEarners} is empty
 * and departments are split by currency: use {@code totalsByCurrency}. {@code earnings} and {@code
 * deductions} carry each component's currency; the older {@code earningsByComponent} / {@code
 * deductionsByComponent} maps are filled only when every line is in {@code currencyCode}.
 */
public record PayrollRunSummaryResponse(
    UUID runId,
    String periodCode,
    int totalEmployees,
    int processedEmployees,
    int errorCount,
    BigDecimal totalGross,
    BigDecimal totalDeductions,
    BigDecimal totalNet,
    BigDecimal totalEmployerCost,
    BigDecimal totalPayrollCost,
    String currencyCode,
    List<PayrollCurrencyTotals> totalsByCurrency,
    Map<String, BigDecimal> earningsByComponent,
    Map<String, BigDecimal> deductionsByComponent,
    List<ComponentTotal> earnings,
    List<ComponentTotal> deductions,
    Map<String, DepartmentPayrollSummary> byDepartment,
    List<EmployeePayrollSummary> topEarners,
    ComparisonWithPreviousPeriod comparison
) {
    /** One pay component's total in one currency (the employees' own, as on their payslips). */
    public record ComponentTotal(String name, String currencyCode, BigDecimal amount) {}

    public record DepartmentPayrollSummary(
        String departmentName,
        String currencyCode,
        int employeeCount,
        BigDecimal totalGross,
        BigDecimal totalNet,
        BigDecimal averageSalary
    ) {}

    public record EmployeePayrollSummary(
        UUID employeeId,
        String employeeName,
        String department,
        String currencyCode,
        BigDecimal gross,
        BigDecimal net
    ) {}

    public record ComparisonWithPreviousPeriod(
        BigDecimal previousGross,
        BigDecimal grossChange,
        BigDecimal grossChangePercentage,
        BigDecimal previousNet,
        BigDecimal netChange,
        BigDecimal netChangePercentage,
        int previousEmployeeCount,
        int employeeCountChange
    ) {}
}
