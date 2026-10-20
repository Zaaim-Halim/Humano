package com.humano.service.payroll.template.model;

import com.humano.domain.enumeration.payroll.Frequency;
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
 * @param rounding      default rounding for emitted amounts
 * @param changelog     what changed in each version, newest first
 * @param sources       legal and official sources, by id
 * @param inputs        employer settings and employee attributes the template reads
 * @param parameters    effective-dated scalar values, by name
 * @param tables        effective-dated lookup tables, by name
 * @param steps         the calculation, in order
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
    Rounding rounding,
    List<ChangelogEntry> changelog,
    Map<String, Source> sources,
    Inputs inputs,
    Map<String, List<DatedValue>> parameters,
    Map<String, Table> tables,
    List<Step> steps,
    List<String> limitations,
    List<TestCase> testCases
) {
    /** {@code id@version}, recorded on every payroll line the template produces. */
    public String ref() {
        return id + "@" + version;
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
}
