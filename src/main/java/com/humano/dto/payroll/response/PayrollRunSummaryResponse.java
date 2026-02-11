package com.humano.dto.payroll.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for payroll run summary with aggregated statistics.
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
    Map<String, BigDecimal> earningsByComponent,
    Map<String, BigDecimal> deductionsByComponent,
    Map<String, DepartmentPayrollSummary> byDepartment,
    List<EmployeePayrollSummary> topEarners,
    ComparisonWithPreviousPeriod comparison
) {
    public record DepartmentPayrollSummary(
        String departmentName,
        int employeeCount,
        BigDecimal totalGross,
        BigDecimal totalNet,
        BigDecimal averageSalary
    ) {}

    public record EmployeePayrollSummary(UUID employeeId, String employeeName, String department, BigDecimal gross, BigDecimal net) {}

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
