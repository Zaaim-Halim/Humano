package com.humano.service.payroll.template.model;

import com.humano.domain.enumeration.payroll.Frequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A worked example a template must reproduce. Every shipped template's cases run in CI.
 *
 * @param name            what the case checks
 * @param evidence        where the expected amounts come from (an official example, or the law
 *                        articles they were derived from)
 * @param periodStart     pay period start
 * @param periodEnd       pay period end
 * @param frequency       pay frequency
 * @param paymentDate     payment date (defaults to the period end)
 * @param grossEarnings   gross earnings in the period
 * @param earningsByComponent the same by pay-component code (defaults to all BASIC)
 * @param socialEarnings  earnings subject to social contributions (defaults to gross)
 * @param socialEarningsByComponent their part per pay-component code (defaults to earningsByComponent)
 * @param taxableEarnings taxable earnings (defaults to gross)
 * @param taxableEarningsByComponent their taxable part per pay-component code (defaults to earningsByComponent)
 * @param reductions      unpaid-leave reductions (defaults to 0)
 * @param preTaxDeductions tenant pre-tax deductions (defaults to the sum by type, else 0)
 * @param preTaxDeductionsByType the same by deduction type (defaults to none)
 * @param fullPeriod      employed full-time for the whole period (defaults to true)
 * @param birthDate       employee's birth date
 * @param employmentStartDate first day of employment (defaults to the period start)
 * @param employedThroughout employed for the whole period (defaults to true)
 * @param employer        employer settings
 * @param employee        employee attributes
 * @param ytd             year-to-date sums before this period, keyed {@code step.SIDE.base|amount}
 * @param expectLines     the lines the template must produce (all of them, in any order)
 * @param expectError     instead of lines: a substring of the error the template must raise
 */
public record TestCase(
    String name,
    String evidence,
    LocalDate periodStart,
    LocalDate periodEnd,
    Frequency frequency,
    LocalDate paymentDate,
    BigDecimal grossEarnings,
    Map<String, BigDecimal> earningsByComponent,
    BigDecimal socialEarnings,
    Map<String, BigDecimal> socialEarningsByComponent,
    BigDecimal taxableEarnings,
    Map<String, BigDecimal> taxableEarningsByComponent,
    BigDecimal reductions,
    BigDecimal preTaxDeductions,
    Map<String, BigDecimal> preTaxDeductionsByType,
    Boolean fullPeriod,
    LocalDate birthDate,
    LocalDate employmentStartDate,
    Boolean employedThroughout,
    Map<String, String> employer,
    Map<String, String> employee,
    Map<String, BigDecimal> ytd,
    List<ExpectedLine> expectLines,
    String expectError
) {
    public record ExpectedLine(String step, LineSide side, BigDecimal amount) {}
}
