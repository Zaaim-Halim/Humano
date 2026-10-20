package com.humano.service.payroll.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.DeductionType;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.enumeration.payroll.PayrollLineCategory;
import com.humano.domain.enumeration.payroll.RunStatus;
import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.Deduction;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollLine;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ConfigurePayrollTemplateRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.DeductionRepository;
import com.humano.repository.payroll.EmployeeTemplateProfileRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollLineRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.repository.payroll.PayrollResultRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.payroll.PayrollTemplateAdoptionRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.payroll.PayrollProcessingService;
import com.humano.service.payroll.template.engine.YtdAccess;
import com.humano.service.payroll.template.model.LineSide;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Bulgarian template through the real payroll run: adopt it, calculate a month for a Bulgarian
 * employee, and check the persisted lines — amounts, labels, percentage rates, bases and the
 * template id@version / step audit tags — then year-to-date reading from posted runs.
 * <p>
 * The run calculates each employee in its own REQUIRES_NEW transaction, so the fixtures are
 * committed (not rolled back) and removed again after each test.
 */
@IntegrationTest
class PayrollTemplatePayrollRunIT {

    private static final String TEMPLATE = "bg-employment";
    private static final String GB_TEMPLATE = "gb-paye-nic";
    private static final String US_TEMPLATE = "us-federal-employment";

    @Autowired
    private PayrollTemplateService templateService;

    @Autowired
    private com.humano.service.payroll.template.catalog.PayrollTemplateCatalog catalog;

    @Autowired
    private PayrollProcessingService payrollService;

    @Autowired
    @Qualifier("tenantTransactionManager")
    private PlatformTransactionManager txManager;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private OrganizationalUnitRepository units;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private CountryRepository countries;

    @Autowired
    private CurrencyRepository currencies;

    @Autowired
    private CompensationRepository compensations;

    @Autowired
    private PayComponentRepository components;

    @Autowired
    private PayrollCalendarRepository calendars;

    @Autowired
    private PayrollPeriodRepository periods;

    @Autowired
    private PayrollRunRepository runs;

    @Autowired
    private PayrollResultRepository results;

    @Autowired
    private PayrollLineRepository lines;

    @Autowired
    private PayrollTemplateAdoptionRepository adoptions;

    @Autowired
    private DeductionRepository deductions;

    @Autowired
    private EmployeeTemplateProfileRepository profiles;

    private TransactionTemplate tx;
    private final List<Runnable> cleanup = new ArrayList<>();
    private Employee employee;
    private PayrollCalendar calendar;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> {
            OrganizationalUnit unit = new OrganizationalUnit();
            unit.setName("Sofia Engineering " + UUID.randomUUID());
            unit.setType(OrganizationalUnitType.TEAM);
            unit = units.saveAndFlush(unit);
            Position position = new Position();
            position.setName("Software Engineer " + UUID.randomUUID());
            position.setLevel("L2");
            position = positions.saveAndFlush(position);

            String login = "bg-" + UUID.randomUUID().toString().substring(0, 8);
            Employee e = new Employee();
            e.setLogin(login);
            e.setEmail(login + "@example.com");
            e.setPassword("x".repeat(60));
            e.setActivated(true);
            e.setFirstName("Ivan");
            e.setLastName("Petrov");
            e.setStatus(EmployeeStatus.ACTIVE);
            e.setStartDate(LocalDate.of(2025, 1, 1));
            e.setBirthDate(LocalDate.of(1985, 4, 12));
            e.setCountry(countries.findAll().stream().filter(c -> c.getCode() == CountryCode.BG).findFirst().orElseThrow());
            e.setPosition(position);
            e.setUnit(unit);
            employee = employees.saveAndFlush(e);

            Compensation compensation = new Compensation();
            compensation.setEmployee(employee);
            compensation.setPosition(position);
            compensation.setCurrency(currencies.findByCode(CurrencyCode.EUR).orElseThrow());
            compensation.setBaseAmount(new BigDecimal("2000.00"));
            compensation.setBasis(Basis.MONTHLY);
            compensation.setEffectiveFrom(LocalDate.of(2025, 1, 1));
            compensations.saveAndFlush(compensation);

            PayrollCalendar c = new PayrollCalendar();
            c.setName("BG monthly " + UUID.randomUUID());
            c.setFrequency(Frequency.MONTHLY);
            c.setTimezone(java.util.TimeZone.getTimeZone("Europe/Sofia"));
            c.setActive(true);
            calendar = calendars.saveAndFlush(c);

            if (components.findAll().stream().noneMatch(pc -> pc.getCode() == PayComponentCode.BASIC)) {
                PayComponent basic = new PayComponent();
                basic.setCode(PayComponentCode.BASIC);
                basic.setName("Basic salary");
                basic.setKind(Kind.EARNING);
                basic.setMeasure(Measurement.AMOUNT);
                basic.setTaxable(true);
                basic.setContributesToSocial(true);
                UUID id = components.saveAndFlush(basic).getId();
                cleanup.add(() -> components.deleteById(id));
            }
            for (PayComponentCode code : List.of(PayComponentCode.TAX_PIT, PayComponentCode.EMPLOYER_CHARGE)) {
                if (components.findAll().stream().noneMatch(pc -> pc.getCode() == code)) {
                    // created by configure(); removed afterwards so other tests see the original setup
                    cleanup.add(() -> components.findAll().stream().filter(pc -> pc.getCode() == code).forEach(components::delete));
                }
            }
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> {
            // Every run on this test's calendar, with all its results and lines.
            List<PayrollRun> ours = runs.findAll(
                (Specification<PayrollRun>) (root, q, cb) -> cb.equal(root.get("period").get("calendar").get("id"), calendar.getId())
            );
            for (PayrollRun run : ours) {
                lines.deleteAll(
                    lines.findAll(
                        (Specification<PayrollLine>) (root, q, cb) -> cb.equal(root.get("result").get("run").get("id"), run.getId())
                    )
                );
                results.deleteAll(
                    results.findAll((Specification<PayrollResult>) (root, q, cb) -> cb.equal(root.get("run").get("id"), run.getId()))
                );
            }
            runs.deleteAll(ours);
            periods.deleteAll(
                periods.findAll((Specification<PayrollPeriod>) (root, q, cb) -> cb.equal(root.get("calendar").get("id"), calendar.getId()))
            );
            calendars.deleteById(calendar.getId());
            compensations.deleteAll(
                compensations.findAll().stream().filter(c -> c.getEmployee().getId().equals(employee.getId())).toList()
            );
            deductions.deleteAll(deductions.findAll().stream().filter(d -> d.getEmployee().getId().equals(employee.getId())).toList());
            for (String templateId : List.of(TEMPLATE, GB_TEMPLATE, US_TEMPLATE)) {
                profiles.findByEmployeeIdAndTemplateId(employee.getId(), templateId).ifPresent(profiles::delete);
                adoptions.findByTemplateId(templateId).ifPresent(adoptions::delete);
            }
            Employee e = employees.findById(employee.getId()).orElseThrow();
            employees.delete(e);
            units.delete(e.getUnit());
            positions.delete(e.getPosition());
            cleanup.forEach(Runnable::run);
        });
    }

    @Test
    void aBulgarianMonthIsCalculatedByTheTemplateAndTaggedForAudit() {
        templateService.configure(TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "62")));
        templateService.saveProfile(TEMPLATE, employee.getId(), Map.of("qualificationGroup", "2"));
        PayrollPeriod march = period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        PayrollRunResponse run = calculate(march);

        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        PayrollResult result = resultOf(run.id());
        // Same amounts as the template's law-derived worked example "ordinary month, born after 1959".
        assertThat(result.getGross()).isEqualByComparingTo("2000.00");
        assertThat(result.getTotalDeductions()).isEqualByComparingTo("448.04"); // 275.60 contributions + 172.44 tax
        assertThat(result.getNet()).isEqualByComparingTo("1551.96");
        assertThat(result.getEmployerCost()).isEqualByComparingTo("2378.40"); // 2000 + 378.40 employer contributions

        List<PayrollLine> templateLines = linesOf(result).stream().filter(l -> l.getTemplateRef() != null).toList();
        assertThat(templateLines).allSatisfy(l -> assertThat(l.getTemplateRef()).startsWith(TEMPLATE + "@"));
        assertThat(templateLines)
            .extracting(l -> l.getTemplateStep() + "/" + l.getLineCategory() + "=" + l.getAmount().stripTrailingZeros().toPlainString())
            .containsExactlyInAnyOrder(
                "pension/WITHHOLDING=131.6",
                "pension/EMPLOYER_CHARGE=164.4",
                "universalFund/WITHHOLDING=44",
                "universalFund/EMPLOYER_CHARGE=56",
                "sickness/WITHHOLDING=28",
                "sickness/EMPLOYER_CHARGE=42",
                "unemployment/WITHHOLDING=8",
                "unemployment/EMPLOYER_CHARGE=12",
                "health/WITHHOLDING=64",
                "health/EMPLOYER_CHARGE=96",
                "workAccident/EMPLOYER_CHARGE=8",
                "incomeTax/INCOME_TAX=172.44"
            );
        PayrollLine pension = templateLines
            .stream()
            .filter(l -> l.getTemplateStep().equals("pension") && l.getLineCategory() == PayrollLineCategory.WITHHOLDING)
            .findFirst()
            .orElseThrow();
        assertThat(pension.displayName()).isEqualTo("Pension fund (DOO) (employee)");
        assertThat(pension.getTaxType()).isEqualTo(TaxType.PENSION_CONTRIBUTION);
        assertThat(pension.getRate()).isEqualByComparingTo("6.58");
        assertThat(pension.getQuantity()).isEqualByComparingTo("2000.00");
        // No generic bracket-tax line alongside the template's tax.
        assertThat(linesOf(result)).filteredOn(l -> l.getLineCategory() == PayrollLineCategory.INCOME_TAX).hasSize(1);
    }

    @Test
    void yearToDateReadsPostedEarlierPeriodsOnly() {
        templateService.configure(TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "62")));
        templateService.saveProfile(TEMPLATE, employee.getId(), Map.of("qualificationGroup", "2"));
        PayrollRunResponse march = calculate(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));
        var template = catalog.find(TEMPLATE).orElseThrow();

        // Calculated but not posted: nothing counts yet.
        assertThat(ytd(template, LocalDate.of(2026, 4, 1))).isEmpty();

        tx.executeWithoutResult(s -> {
            PayrollRun run = runs.findById(march.id()).orElseThrow();
            run.setStatus(RunStatus.POSTED);
            runs.save(run);
        });
        Map<String, BigDecimal> april = ytd(template, LocalDate.of(2026, 4, 1));
        assertThat(april.get(YtdAccess.key("pension", LineSide.EMPLOYEE, "base"))).isEqualByComparingTo("2000.00");
        assertThat(april.get(YtdAccess.key("pension", LineSide.EMPLOYEE, "amount"))).isEqualByComparingTo("131.60");
        assertThat(april.get(YtdAccess.key("incomeTax", LineSide.TAX, "amount"))).isEqualByComparingTo("172.44");

        // The period itself, and a new tax year, see nothing.
        assertThat(ytd(template, LocalDate.of(2026, 3, 1))).isEmpty();
        assertThat(ytd(template, LocalDate.of(2027, 1, 1))).isEmpty();
    }

    @Test
    void anEmployeeTheTemplateCantComputeGetsAnErrorNotAGenericFallback() {
        templateService.configure(TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "62")));
        // No profile: the qualification group needed for the minimum-insurable-income lookup is missing.
        PayrollRunResponse run = calculate(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)));

        assertThat(run.validationErrors())
            .filteredOn(err -> employee.getId().equals(err.employeeId()))
            .singleElement()
            .satisfies(err -> assertThat(err.message()).contains("qualificationGroup"));
    }

    @Test
    void templateChangesChangeTheRunFingerprint() {
        templateService.configure(TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "62")));
        String before = tx.execute(s -> templateService.fingerprint());
        templateService.configure(TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "63")));
        String afterSettings = tx.execute(s -> templateService.fingerprint());
        templateService.saveProfile(TEMPLATE, employee.getId(), Map.of("qualificationGroup", "2"));
        String afterProfile = tx.execute(s -> templateService.fingerprint());

        assertThat(before).contains("bg-employment@");
        assertThat(afterSettings).isNotEqualTo(before);
        assertThat(afterProfile).isNotEqualTo(afterSettings);
    }

    @Test
    void ukPayeIsCumulativeByPaymentDateAcrossPostedRuns() {
        // A UK employee paid in GBP: £1,000 in April (paid 30 April, tax month 1), then £3,000 in May.
        tx.executeWithoutResult(s -> {
            Employee e = employees.findById(employee.getId()).orElseThrow();
            e.setCountry(countries.findAll().stream().filter(c -> c.getCode() == CountryCode.GB).findFirst().orElseThrow());
            e.setBirthDate(LocalDate.of(1990, 5, 1));
            employees.save(e);
            setPay("1000.00", CurrencyCode.GBP);
        });
        templateService.configure(GB_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        templateService.saveProfile(GB_TEMPLATE, employee.getId(), Map.of("taxCode", "1257L", "niCategory", "A"));

        // April 1-30 starts before the tax year (6 April) but is paid in tax month 1.
        PayrollRunResponse april = calculate(period(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)));
        assertThat(april.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        List<PayrollLine> aprilLines = linesOf(resultOf(april.id())).stream().filter(l -> l.getTemplateRef() != null).toList();
        // £1,000 is under the month-1 free pay of £1,048.26: no tax, but the zero line is recorded for pay to date.
        assertThat(aprilLines)
            .extracting(l -> l.getTemplateStep() + "/" + l.getLineCategory() + "=" + l.getAmount().stripTrailingZeros().toPlainString())
            .containsExactlyInAnyOrder("incomeTax/INCOME_TAX=0", "nationalInsurance/EMPLOYER_CHARGE=87.45");
        tx.executeWithoutResult(s -> {
            PayrollRun run = runs.findById(april.id()).orElseThrow();
            run.setStatus(RunStatus.POSTED);
            runs.save(run);
            setPay("3000.00", CurrencyCode.GBP);
        });

        PayrollRunResponse may = calculate(period(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));
        assertThat(may.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        // Month 2: £4,000 to date − 2 × £1,048.26 = £1,903 at 20% = £380.60. Without April's recorded pay it
        // would be £180.60; with April placed by its 1 April start date it would fall in the previous tax year.
        assertThat(linesOf(resultOf(may.id())))
            .filteredOn(l -> l.getTemplateRef() != null)
            .extracting(l -> l.getTemplateStep() + "/" + l.getLineCategory() + "=" + l.getAmount().stripTrailingZeros().toPlainString())
            .containsExactlyInAnyOrder(
                "incomeTax/INCOME_TAX=380.6",
                "nationalInsurance/WITHHOLDING=156.16",
                "nationalInsurance/EMPLOYER_CHARGE=387.45"
            );
    }

    @Test
    void usWageBasesAndPreTaxDeductionsCarryAcrossPostedRuns() {
        // $120,000 a month with a $1,000 pre-tax 401(k) deferral: income tax wages $119,000, FICA wages $120,000.
        tx.executeWithoutResult(s -> {
            Employee e = employees.findById(employee.getId()).orElseThrow();
            e.setCountry(countries.findAll().stream().filter(c -> c.getCode() == CountryCode.US).findFirst().orElseThrow());
            employees.save(e);
            setPay("120000.00", CurrencyCode.USD);
            if (components.findAll().stream().noneMatch(pc -> pc.getCode() == PayComponentCode.DEDUCTION)) {
                PayComponent deduction = new PayComponent();
                deduction.setCode(PayComponentCode.DEDUCTION);
                deduction.setName("Deduction");
                deduction.setKind(Kind.DEDUCTION);
                deduction.setMeasure(Measurement.AMOUNT);
                deduction.setTaxable(false);
                deduction.setContributesToSocial(false);
                UUID id = components.saveAndFlush(deduction).getId();
                cleanup.add(() -> components.deleteById(id));
            }
            Deduction k401 = new Deduction();
            k401.setEmployee(e);
            k401.setType(DeductionType.RETIREMENT);
            k401.setAmount(new BigDecimal("1000.00"));
            k401.setIsPreTax(true);
            k401.setDescription("401(k)");
            k401.setEffectiveFrom(LocalDate.of(2026, 1, 1));
            k401.setCurrency(currencies.findByCode(CurrencyCode.USD).orElseThrow());
            deductions.save(k401);
        });
        templateService.configure(US_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        templateService.saveProfile(US_TEMPLATE, employee.getId(), Map.of("filingStatus", "SINGLE"));

        // January. Income tax: ($119,000 × 12 − $8,600) in the top single bracket = $478,360.25 a year / 12.
        PayrollRunResponse january = calculate(period(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertThat(january.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        assertThat(templateLines(january)).containsExactlyInAnyOrder(
            "federalIncomeTax/INCOME_TAX=39863.35",
            "socialSecurity/WITHHOLDING=7440",
            "socialSecurity/EMPLOYER_CHARGE=7440",
            "medicare/WITHHOLDING=1740",
            "medicare/EMPLOYER_CHARGE=1740",
            "futa/EMPLOYER_CHARGE=42"
        );
        tx.executeWithoutResult(s -> {
            PayrollRun run = runs.findById(january.id()).orElseThrow();
            run.setStatus(RunStatus.POSTED);
            runs.save(run);
        });

        // February: $64,500 left under the $184,500 wage base, $40,000 over the $200,000 Additional Medicare
        // threshold, FUTA's $7,000 already used.
        PayrollRunResponse february = calculate(period(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
        assertThat(february.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        assertThat(templateLines(february)).containsExactlyInAnyOrder(
            "federalIncomeTax/INCOME_TAX=39863.35",
            "socialSecurity/WITHHOLDING=3999",
            "socialSecurity/EMPLOYER_CHARGE=3999",
            "medicare/WITHHOLDING=1740",
            "medicare/EMPLOYER_CHARGE=1740",
            "additionalMedicare/WITHHOLDING=360"
        );
    }

    private List<String> templateLines(PayrollRunResponse run) {
        return linesOf(resultOf(run.id()))
            .stream()
            .filter(l -> l.getTemplateRef() != null)
            .map(l -> l.getTemplateStep() + "/" + l.getLineCategory() + "=" + l.getAmount().stripTrailingZeros().toPlainString())
            .toList();
    }

    private void setPay(String amount, CurrencyCode currency) {
        compensations
            .findAll()
            .stream()
            .filter(c -> c.getEmployee().getId().equals(employee.getId()))
            .forEach(c -> {
                c.setBaseAmount(new BigDecimal(amount));
                c.setCurrency(currencies.findByCode(currency).orElseThrow());
                compensations.save(c);
            });
    }

    private Map<String, BigDecimal> ytd(com.humano.service.payroll.template.model.PayrollTemplate template, LocalDate periodStart) {
        return tx.execute(s -> templateService.yearToDate(employee.getId(), template, periodStart, periodStart));
    }

    private PayrollPeriod period(LocalDate start, LocalDate end) {
        return tx.execute(s -> {
            PayrollPeriod p = new PayrollPeriod();
            p.setCalendar(calendar);
            p.setStartDate(start);
            p.setEndDate(end);
            p.setPaymentDate(end);
            p.setClosed(false);
            p.setCode("BG-" + start + "-" + UUID.randomUUID().toString().substring(0, 6));
            return periods.saveAndFlush(p);
        });
    }

    private PayrollRunResponse calculate(PayrollPeriod period) {
        PayrollRunResponse run = payrollService.initiatePayrollRun(
            new InitiatePayrollRunRequest(period.getId(), InitiatePayrollRunRequest.PayrollScope.ALL, List.of(), false, null, null)
        );
        return payrollService.calculatePayroll(run.id());
    }

    private PayrollResult resultOf(UUID runId) {
        return tx.execute(s ->
            results
                .findAll(
                    (Specification<PayrollResult>) (root, q, cb) ->
                        cb.and(cb.equal(root.get("run").get("id"), runId), cb.equal(root.get("employee").get("id"), employee.getId()))
                )
                .get(0)
        );
    }

    private List<PayrollLine> linesOf(PayrollResult result) {
        return tx.execute(s -> {
            List<PayrollLine> found = lines.findAll(
                (Specification<PayrollLine>) (root, q, cb) -> cb.equal(root.get("result").get("id"), result.getId())
            );
            found.forEach(l -> l.getComponent().getName()); // initialise for displayName() outside the transaction
            return found;
        });
    }
}
