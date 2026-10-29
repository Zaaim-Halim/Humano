package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.enumeration.payroll.PayrollLineCategory;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayRule;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollLine;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ConfigurePayrollTemplateRequest;
import com.humano.dto.payroll.request.CreatePayRuleRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.EmployeeTemplateProfileRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayRuleRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollLineRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.repository.payroll.PayrollResultRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.payroll.PayrollTemplateAdoptionRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.payroll.template.PayrollTemplateService;
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
 * Formula pay rules for an employee a payroll template covers, as the pay rules screen describes them:
 * earnings are worked out first and reach the template's bases through their taxable and social
 * switches; formula deductions come off net pay after it and change nothing it computes; formulas on
 * income tax and employer charges don't run for its employees.
 */
@IntegrationTest
class PayRulesWithTemplateIT {

    private static final String BG_TEMPLATE = "bg-employment";
    private static final String MARKER = "pay rules with a template IT";

    @Autowired
    private PayrollProcessingService payrollService;

    @Autowired
    private PayComponentService payComponentService;

    @Autowired
    private PayrollTemplateService templateService;

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
    private PayRuleRepository rules;

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
    private EmployeeTemplateProfileRepository profiles;

    @Autowired
    private com.humano.repository.payroll.OrganizationSettingsRepository organizationSettings;

    private TransactionTemplate tx;
    private final List<Runnable> cleanup = new ArrayList<>();
    private Employee employee;
    private PayrollCalendar calendar;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // Enabling a template sets the workspace's default currency: put it back afterwards.
        tx.executeWithoutResult(s -> {
            com.humano.domain.payroll.OrganizationSettings settings = organizationSettings.findAll().stream().findFirst().orElse(null);
            UUID settingsId = settings == null ? null : settings.getId();
            UUID currencyId = settings == null || settings.getDefaultCurrency() == null ? null : settings.getDefaultCurrency().getId();
            cleanup.add(() -> {
                if (settingsId == null) {
                    organizationSettings.deleteAll();
                    return;
                }
                organizationSettings
                    .findById(settingsId)
                    .ifPresent(o -> {
                        o.setDefaultCurrency(currencyId == null ? null : currencies.findById(currencyId).orElseThrow());
                        organizationSettings.save(o);
                    });
            });
        });
        tx.executeWithoutResult(s -> {
            OrganizationalUnit unit = new OrganizationalUnit();
            unit.setName("Pay rules team " + UUID.randomUUID());
            unit.setType(OrganizationalUnitType.TEAM);
            unit = units.saveAndFlush(unit);
            Position position = new Position();
            position.setName("Analyst " + UUID.randomUUID());
            position.setLevel("L2");
            position = positions.saveAndFlush(position);

            String login = "rules-" + UUID.randomUUID().toString().substring(0, 8);
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
            c.setName("Pay rules monthly " + UUID.randomUUID());
            c.setFrequency(Frequency.MONTHLY);
            c.setTimezone(java.util.TimeZone.getTimeZone("Europe/Sofia"));
            c.setActive(true);
            calendar = calendars.saveAndFlush(c);

            component(PayComponentCode.BASIC, Kind.EARNING, true);
            // Overtime with a formula: taxable and subject to social contributions unless a test switches it.
            component(PayComponentCode.OT, Kind.EARNING, true);
            component(PayComponentCode.DEDUCTION, Kind.DEDUCTION, false);
            for (PayComponentCode code : List.of(PayComponentCode.TAX_PIT, PayComponentCode.EMPLOYER_CHARGE)) {
                if (components.findAll().stream().noneMatch(pc -> pc.getCode() == code)) {
                    // created by the template's adoption; removed afterwards so other tests see the original setup
                    cleanup.add(() -> components.findAll().stream().filter(pc -> pc.getCode() == code).forEach(components::delete));
                }
            }
        });
        templateService.configure(BG_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "62")));
        templateService.saveProfile(BG_TEMPLATE, employee.getId(), Map.of("qualificationGroup", "2"));
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> {
            rules.deleteAll(rules.findAll().stream().filter(r -> MARKER.equals(r.getDescription())).toList());
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
            profiles.findByEmployeeIdAndTemplateId(employee.getId(), BG_TEMPLATE).ifPresent(profiles::delete);
            adoptions.findByTemplateId(BG_TEMPLATE).ifPresent(adoptions::delete);
            Employee e = employees.findById(employee.getId()).orElseThrow();
            employees.delete(e);
            units.delete(e.getUnit());
            positions.delete(e.getPosition());
            cleanup.forEach(Runnable::run);
        });
    }

    @Test
    void theScreenListsTheTemplatesPayrollUses() {
        assertThat(templateService.inUse()).anySatisfy(t -> {
            assertThat(t.id()).isEqualTo(BG_TEMPLATE);
            assertThat(t.country()).isEqualTo("BG");
        });
    }

    @Test
    void formulaRulesAroundATemplate() {
        rule(PayComponentCode.OT, "100");
        UUID deduction = rule(PayComponentCode.DEDUCTION, "50");
        rule(PayComponentCode.TAX_PIT, "999");
        rule(PayComponentCode.EMPLOYER_CHARGE, "888");

        PayrollRunResponse march = calculate(month(2026, 3));
        List<PayrollLine> marchLines = ourLines(march);

        // The earning formula runs first and its amount reaches the template's contribution base.
        assertThat(marchLines)
            .filteredOn(l -> l.getComponent().getCode() == PayComponentCode.OT)
            .singleElement()
            .satisfies(l -> assertThat(l.getAmount()).isEqualByComparingTo("100"));
        assertThat(pensionBase(marchLines)).isEqualByComparingTo("2100");
        // The deduction formula comes off net pay, after the template.
        assertThat(marchLines)
            .filteredOn(l -> l.getLineCategory() == PayrollLineCategory.POST_TAX_DEDUCTION)
            .singleElement()
            .satisfies(l -> assertThat(l.getAmount()).isEqualByComparingTo("50"));
        // Income tax and employer charge formulas don't run for the template's employee.
        assertThat(marchLines)
            .filteredOn(l -> l.getTemplateRef() == null)
            .noneMatch(
                l ->
                    l.getComponent().getCode() == PayComponentCode.TAX_PIT || l.getComponent().getCode() == PayComponentCode.EMPLOYER_CHARGE
            );
        assertThat(marchLines).filteredOn(l -> l.getTemplateRef() != null).isNotEmpty();

        // Without the deduction, the template computes exactly the same.
        payComponentService.setRuleActive(deduction, false);
        PayrollRunResponse april = calculate(month(2026, 4));
        assertThat(templateAmounts(ourLines(april))).isEqualTo(templateAmounts(marchLines));
    }

    @Test
    void anEarningOutsideSocialContributionsStaysOutOfTheTemplatesBase() {
        rule(PayComponentCode.OT, "100");
        tx.executeWithoutResult(s ->
            components
                .findAll()
                .stream()
                .filter(c -> c.getCode() == PayComponentCode.OT)
                .forEach(c -> {
                    c.setContributesToSocial(false);
                    components.save(c);
                })
        );

        List<PayrollLine> mayLines = ourLines(calculate(month(2026, 5)));

        assertThat(mayLines).anyMatch(l -> l.getComponent().getCode() == PayComponentCode.OT);
        assertThat(pensionBase(mayLines)).isEqualByComparingTo("2000");
    }

    /** The pay component with this code, created for the test if missing; its switches are put back afterwards. */
    private void component(PayComponentCode code, Kind kind, boolean taxableAndSocial) {
        PayComponent existing = components.findAll().stream().filter(pc -> pc.getCode() == code).findFirst().orElse(null);
        if (existing == null) {
            PayComponent c = new PayComponent();
            c.setCode(code);
            c.setName(code.name());
            c.setKind(kind);
            c.setMeasure(Measurement.AMOUNT);
            c.setTaxable(taxableAndSocial);
            c.setContributesToSocial(taxableAndSocial);
            UUID id = components.saveAndFlush(c).getId();
            cleanup.add(() -> components.deleteById(id));
            return;
        }
        Boolean taxable = existing.getTaxable();
        Boolean social = existing.getContributesToSocial();
        existing.setTaxable(taxableAndSocial);
        existing.setContributesToSocial(taxableAndSocial);
        components.saveAndFlush(existing);
        UUID id = existing.getId();
        cleanup.add(() ->
            components
                .findById(id)
                .ifPresent(c -> {
                    c.setTaxable(taxable);
                    c.setContributesToSocial(social);
                    components.save(c);
                })
        );
    }

    /** Adds an active rule with this formula; returns its id. */
    private UUID rule(PayComponentCode code, String formula) {
        UUID componentId = tx.execute(s -> components.findAll().stream().filter(c -> c.getCode() == code).findFirst().orElseThrow().getId()
        );
        payComponentService.createRule(new CreatePayRuleRequest(componentId, formula, MARKER, null, null, null, null, true));
        return tx.execute(s ->
            rules
                .findAll()
                .stream()
                .filter(r -> MARKER.equals(r.getDescription()) && r.getPayComponent().getId().equals(componentId))
                .map(PayRule::getId)
                .findFirst()
                .orElseThrow()
        );
    }

    /** The base of the template's pension contribution line (template lines keep their base as the quantity). */
    private BigDecimal pensionBase(List<PayrollLine> resultLines) {
        return resultLines
            .stream()
            .filter(l -> "pension".equals(l.getTemplateStep()) && l.getLineCategory() == PayrollLineCategory.WITHHOLDING)
            .findFirst()
            .orElseThrow()
            .getQuantity();
    }

    private List<String> templateAmounts(List<PayrollLine> resultLines) {
        return resultLines
            .stream()
            .filter(l -> l.getTemplateRef() != null)
            .map(l -> l.getTemplateStep() + "/" + l.getLineCategory() + "=" + l.getAmount().stripTrailingZeros().toPlainString())
            .sorted()
            .toList();
    }

    private PayrollPeriod month(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return tx.execute(s -> {
            PayrollPeriod p = new PayrollPeriod();
            p.setCalendar(calendar);
            p.setStartDate(start);
            p.setEndDate(end);
            p.setPaymentDate(end);
            p.setClosed(false);
            p.setCode("PR-" + start + "-" + UUID.randomUUID().toString().substring(0, 6));
            return periods.saveAndFlush(p);
        });
    }

    private PayrollRunResponse calculate(PayrollPeriod period) {
        PayrollRunResponse run = payrollService.initiatePayrollRun(
            new InitiatePayrollRunRequest(period.getId(), InitiatePayrollRunRequest.PayrollScope.ALL, List.of(), false, null, null)
        );
        PayrollRunResponse calculated = payrollService.calculatePayroll(run.id());
        assertThat(calculated.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        return calculated;
    }

    /** Our employee's lines in the run. */
    private List<PayrollLine> ourLines(PayrollRunResponse run) {
        return tx.execute(s -> {
            List<PayrollLine> found = lines.findAll(
                (Specification<PayrollLine>) (root, q, cb) ->
                    cb.and(
                        cb.equal(root.get("result").get("run").get("id"), run.id()),
                        cb.equal(root.get("result").get("employee").get("id"), employee.getId())
                    )
            );
            found.forEach(l -> l.getComponent().getCode()); // initialise outside the transaction
            return found;
        });
    }
}
