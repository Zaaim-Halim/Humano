package com.humano.service.payroll.template.engine;

import com.humano.domain.enumeration.payroll.Frequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One employee's pay period, as a template sees it.
 *
 * @param periodStart     first day of the pay period
 * @param periodEnd       last day of the pay period
 * @param paymentDate     date the pay is paid (selects the tax period under a PAYMENT_DATE basis)
 * @param frequency       pay frequency of the calendar
 * @param currency        ISO 4217 code of the employee's pay
 * @param grossEarnings   all earnings in the period
 * @param earningsByComponent the same by pay-component code (e.g. BASIC, BONUS), for laws that tax some
 *                        kinds of pay differently
 * @param socialEarnings  earnings whose pay component is subject to social contributions
 * @param socialEarningsByComponent the same by pay-component code; null means every earning is social
 * @param taxableEarnings earnings whose pay component is taxable
 * @param taxableEarningsByComponent the same by pay-component code; null means every earning is taxable
 * @param reductions      remuneration not earned in the period (unpaid-leave deductions)
 * @param preTaxDeductions other deductions the tenant marked pre-tax (e.g. pension or 401(k) plans);
 *                        each template decides whether its law lets them reduce a base
 * @param preTaxDeductionsByType the same deductions by deduction type (e.g. RETIREMENT, INSURANCE),
 *                        for laws that treat them differently per base
 * @param fullPeriod      employed full-time for the whole period with no unpaid leave
 * @param fte             full-time equivalent (1 when unknown)
 * @param birthDate       date of birth, or null when unknown
 * @param employmentStartDate first day of employment, or null when unknown
 * @param employedThroughout employed from the first to the last day of the period
 * @param employer        the tenant's raw settings for the template
 * @param employee        the employee's raw attributes for the template
 * @param ytd             year-to-date sums before this period ({@link YtdAccess} keys)
 * @param leavePay        the part of the earnings paid for approved leave days, by leave type (e.g. SICK), for
 *                        leave types with a leave pay rule; laws that treat sick or maternity pay apart read it
 * @param leaveDays       calendar days of that leave within the period, by leave type
 * @param unpaidLeaveDays the calendar days among them the rule leaves wholly unpaid (e.g. days a social insurer pays)
 */
public record TemplateInput(
    LocalDate periodStart,
    LocalDate periodEnd,
    LocalDate paymentDate,
    Frequency frequency,
    String currency,
    BigDecimal grossEarnings,
    Map<String, BigDecimal> earningsByComponent,
    BigDecimal socialEarnings,
    Map<String, BigDecimal> socialEarningsByComponent,
    BigDecimal taxableEarnings,
    Map<String, BigDecimal> taxableEarningsByComponent,
    BigDecimal reductions,
    BigDecimal preTaxDeductions,
    Map<String, BigDecimal> preTaxDeductionsByType,
    boolean fullPeriod,
    BigDecimal fte,
    LocalDate birthDate,
    LocalDate employmentStartDate,
    boolean employedThroughout,
    Map<String, String> employer,
    Map<String, String> employee,
    Map<String, BigDecimal> ytd,
    Map<String, BigDecimal> leavePay,
    Map<String, BigDecimal> leaveDays,
    Map<String, BigDecimal> unpaidLeaveDays
) {
    /** A period without paid leave. */
    public TemplateInput(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate paymentDate,
        Frequency frequency,
        String currency,
        BigDecimal grossEarnings,
        Map<String, BigDecimal> earningsByComponent,
        BigDecimal socialEarnings,
        Map<String, BigDecimal> socialEarningsByComponent,
        BigDecimal taxableEarnings,
        Map<String, BigDecimal> taxableEarningsByComponent,
        BigDecimal reductions,
        BigDecimal preTaxDeductions,
        Map<String, BigDecimal> preTaxDeductionsByType,
        boolean fullPeriod,
        BigDecimal fte,
        LocalDate birthDate,
        LocalDate employmentStartDate,
        boolean employedThroughout,
        Map<String, String> employer,
        Map<String, String> employee,
        Map<String, BigDecimal> ytd
    ) {
        this(
            periodStart,
            periodEnd,
            paymentDate,
            frequency,
            currency,
            grossEarnings,
            earningsByComponent,
            socialEarnings,
            socialEarningsByComponent,
            taxableEarnings,
            taxableEarningsByComponent,
            reductions,
            preTaxDeductions,
            preTaxDeductionsByType,
            fullPeriod,
            fte,
            birthDate,
            employmentStartDate,
            employedThroughout,
            employer,
            employee,
            ytd,
            Map.of(),
            Map.of(),
            Map.of()
        );
    }
}
