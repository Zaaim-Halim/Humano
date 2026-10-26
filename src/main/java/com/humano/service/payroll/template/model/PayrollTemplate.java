package com.humano.service.payroll.template.model;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.payroll.LeavePayBand;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A statutory payroll template: one jurisdiction's income tax and social contributions, described
 * as data. Templates ship with the application under {@code payroll-templates/}; tenants adopt them
 * by id and always run the shipped version, so an updated template reaches every adopter.
 * <p>
 * A template is pure declaration — dated parameters, lookup tables and ordered {@link Step}s whose
 * expressions are evaluated by {@code PayrollTemplateEngine}. Where a rule can't be declared (e.g. a
 * published tax algorithm), a step delegates to a code extension by id.
 *
 * @param schemaVersion template format version (currently 1)
 * @param id            stable identifier tenants adopt, e.g. {@code bg-employment}
 * @param name          display name
 * @param version       content version; changes whenever any value or rule changes
 * @param status        how the template's results have been checked
 * @param jurisdiction  where it applies
 * @param validity      the dates the template has data for; every parameter and table covers all of it
 * @param currency      ISO 4217 code all amounts are in
 * @param payFrequencies pay frequencies the template supports
 * @param taxYearStart  first day of the tax year as {@code --MM-DD} (year-to-date sums reset here)
 * @param dateBasis     which date places a payment in the tax year and selects dated values: the
 *                      period's dates (default) or the payment date (e.g. UK PAYE)
 * @param rounding      default rounding for emitted amounts
 * @param changelog     what changed in each version, newest first
 * @param sources       legal and official sources, by id
 * @param inputs        employer settings and employee attributes the template reads
 * @param parameters    effective-dated scalar values, by name
 * @param tables        effective-dated lookup tables, by name
 * @param steps         the calculation, in order
 * @param leavePay      statutory pay for types of leave (e.g. sick pay), applied as a leave pay rule wherever
 *                      the workspace has none of its own; null or empty for none
 * @param limitations   cases the template does not handle (shown to tenants)
 * @param testCases     worked examples the template must reproduce
 */
public record PayrollTemplate(
    int schemaVersion,
    String id,
    String name,
    String version,
    TemplateStatus status,
    Jurisdiction jurisdiction,
    DateRange validity,
    String currency,
    List<Frequency> payFrequencies,
    String taxYearStart,
    DateBasis dateBasis,
    Rounding rounding,
    List<ChangelogEntry> changelog,
    Map<String, Source> sources,
    Inputs inputs,
    Map<String, List<DatedValue>> parameters,
    Map<String, Table> tables,
    List<Step> steps,
    List<LeavePay> leavePay,
    List<String> limitations,
    List<TestCase> testCases
) {
    /** {@code id@version}, recorded on every payroll line the template produces. */
    public String ref() {
        return id + "@" + version;
    }

    /** The date basis, defaulting to {@link DateBasis#PERIOD}. */
    public DateBasis effectiveDateBasis() {
        return dateBasis != null ? dateBasis : DateBasis.PERIOD;
    }

    /**
     * PERIOD: the pay period's dates fall within the validity, select dated values and place the payment
     * in a tax year. PAYMENT_DATE: the payment date does (UK PAYE: "tax week/month covering the date of
     * payment").
     */
    public enum DateBasis {
        PERIOD,
        PAYMENT_DATE,
    }

    public record Jurisdiction(String country, String subdivision) {}

    public record DateRange(LocalDate from, LocalDate to) {
        public boolean contains(LocalDate date) {
            return !date.isBefore(from) && !date.isAfter(to);
        }
    }

    public record ChangelogEntry(String version, LocalDate date, String summary) {}

    public record Source(String title, String url, LocalDate retrieved) {}

    public record Rounding(int scale, String mode) {}

    public record Inputs(List<InputField> employer, List<InputField> employee) {}

    /**
     * What the law makes the employer pay for a type of leave, as a leave pay rule: the share of the daily
     * salary not paid, by stretch of the absence. Always deducted before tax (pay not earned).
     *
     * @param leaveType           the leave type it applies to
     * @param deductionPercentage share not paid on days no band covers, 0 to 100
     * @param payBands            stretches of the absence deducted at their own percentage
     * @param source              id of the source the rule comes from
     * @param note                what the rule leaves out (e.g. waiting periods), shown with it
     */
    public record LeavePay(LeaveType leaveType, BigDecimal deductionPercentage, List<LeavePayBand> payBands, String source, String note) {}

    /** The statutory leave pay rules, never null. */
    public List<LeavePay> effectiveLeavePay() {
        return leavePay == null ? List.of() : leavePay;
    }
}
