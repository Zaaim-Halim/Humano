package com.humano.dto.payroll.response;

import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.TaxType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A shipped statutory payroll template as tenants see it in the catalog, with this tenant's adoption.
 *
 * @param status     OFFICIAL_VECTORS, LAW_DERIVED or DRAFT — how the template's results were checked
 * @param adoptable  whether the template may be enabled (drafts can't)
 * @param ledgers    tax types the template produces; tenant tax brackets / withholdings of these types are
 *                   ignored for covered employees
 * @param testCases  how many worked examples the template reproduces
 * @param leavePay   how sick, maternity and unpaid leave are paid in the template's country
 * @param adoption   this tenant's adoption, or null if never adopted
 */
public record PayrollTemplateResponse(
    String id,
    String name,
    String version,
    String status,
    String country,
    String subdivision,
    LocalDate validFrom,
    LocalDate validTo,
    String currency,
    List<Frequency> payFrequencies,
    String taxYearStart,
    boolean adoptable,
    List<ChangelogEntry> changelog,
    List<Source> sources,
    List<Input> employerInputs,
    List<Input> employeeInputs,
    List<TaxType> ledgers,
    List<String> limitations,
    int testCases,
    List<LeaveCoverage> leavePay,
    Adoption adoption
) {
    public record ChangelogEntry(String version, LocalDate date, String summary) {}

    public record Source(String id, String title, String url, LocalDate retrieved) {}

    /** A setting or attribute the template reads; {@code type} is STRING, INTEGER, DECIMAL, BOOLEAN or ENUM. */
    public record Input(
        String key,
        String type,
        boolean required,
        List<String> values,
        String pattern,
        BigDecimal min,
        BigDecimal max,
        String defaultValue,
        String description
    ) {}

    /**
     * How payroll pays one leave type in the template's country. Without a rule ({@code NONE}) approved
     * leave is paid in full and templates don't see it.
     *
     * @param rule WORKSPACE (the workspace's own rule), TEMPLATE (this template's statutory rule, applied while
     *             it is enabled and the workspace has none) or NONE
     * @param note what the template's statutory rule leaves out, for TEMPLATE
     */
    public record LeaveCoverage(LeaveType leaveType, String rule, String note) {}

    /**
     * @param updateAvailable the shipped version differs from the version an admin last acknowledged
     */
    public record Adoption(boolean enabled, Map<String, String> settings, String acknowledgedVersion, boolean updateAvailable) {}
}
