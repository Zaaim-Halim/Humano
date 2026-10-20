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
 * @param frequency       pay frequency of the calendar
 * @param currency        ISO 4217 code of the employee's pay
 * @param grossEarnings   all earnings in the period
 * @param socialEarnings  earnings whose pay component is subject to social contributions
 * @param taxableEarnings earnings whose pay component is taxable
 * @param reductions      remuneration not earned in the period (unpaid-leave deductions)
 * @param preTaxDeductions other deductions the tenant marked pre-tax (e.g. pension or 401(k) plans);
 *                        each template decides whether its law lets them reduce a base
 * @param fullPeriod      employed full-time for the whole period with no unpaid leave
 * @param fte             full-time equivalent (1 when unknown)
 * @param birthDate       date of birth, or null when unknown
 * @param employer        the tenant's raw settings for the template
 * @param employee        the employee's raw attributes for the template
 * @param ytd             year-to-date sums before this period ({@link YtdAccess} keys)
 */
public record TemplateInput(
    LocalDate periodStart,
    LocalDate periodEnd,
    Frequency frequency,
    String currency,
    BigDecimal grossEarnings,
    BigDecimal socialEarnings,
    BigDecimal taxableEarnings,
    BigDecimal reductions,
    BigDecimal preTaxDeductions,
    boolean fullPeriod,
    BigDecimal fte,
    LocalDate birthDate,
    Map<String, String> employer,
    Map<String, String> employee,
    Map<String, BigDecimal> ytd
) {}
