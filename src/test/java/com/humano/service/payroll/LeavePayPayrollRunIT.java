package com.humano.service.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.humano.IntegrationTest;
import com.humano.domain.enumeration.CountryCode;
import com.humano.domain.enumeration.CurrencyCode;
import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.hr.OrganizationalUnitType;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.enumeration.payroll.Frequency;
import com.humano.domain.enumeration.payroll.Kind;
import com.humano.domain.enumeration.payroll.Measurement;
import com.humano.domain.enumeration.payroll.PayComponentCode;
import com.humano.domain.enumeration.payroll.PayrollLineCategory;
import com.humano.domain.hr.LeaveRequest;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.LeavePayBand;
import com.humano.domain.payroll.LeaveTypeRule;
import com.humano.domain.payroll.PayComponent;
import com.humano.domain.payroll.PayrollCalendar;
import com.humano.domain.payroll.PayrollLine;
import com.humano.domain.payroll.PayrollPeriod;
import com.humano.domain.payroll.PayrollResult;
import com.humano.domain.payroll.PayrollRun;
import com.humano.domain.shared.Country;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ConfigurePayrollTemplateRequest;
import com.humano.dto.payroll.request.CreateLeaveTypeRuleRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.request.UpdateLeaveTypeRuleRequest;
import com.humano.dto.payroll.response.LeaveTypeRuleResponse;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.dto.payroll.response.PayrollTemplateResponse;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.CountryRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.EmployeeTemplateProfileRepository;
import com.humano.repository.payroll.LeaveTypeRuleRepository;
import com.humano.repository.payroll.PayComponentRepository;
import com.humano.repository.payroll.PayrollCalendarRepository;
import com.humano.repository.payroll.PayrollLineRepository;
import com.humano.repository.payroll.PayrollPeriodRepository;
import com.humano.repository.payroll.PayrollResultRepository;
import com.humano.repository.payroll.PayrollRunRepository;
import com.humano.repository.payroll.PayrollTemplateAdoptionRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
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
 * Sick, parental and unpaid leave through the real payroll run: leave pay rules (with pay bands
 * counted from the absence's first day) deduct approved leave by working day, and payroll templates
 * see the deduction as pay not earned.
 * <p>
 * The run calculates each employee in its own REQUIRES_NEW transaction, so the fixtures are
 * committed (not rolled back) and removed again after each test.
 */
@IntegrationTest
class LeavePayPayrollRunIT {

    private static final String BG_TEMPLATE = "bg-employment";
    private static final String ES_TEMPLATE = "es-employment";
    private static final String HU_TEMPLATE = "hu-employment";
    private static final String AL_TEMPLATE = "al-employment";
    private static final String EE_TEMPLATE = "ee-employment";
    private static final String HR_TEMPLATE = "hr-employment";
    private static final String PL_TEMPLATE = "pl-employment";

    @Autowired
    private PayrollProcessingService payrollService;

    @Autowired
    private LeaveTypeRuleService leaveTypeRuleService;

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
    private LeaveRequestRepository leaveRequests;

    @Autowired
    private LeaveTypeRuleRepository leaveRules;

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
            unit.setName("Leave pay team " + UUID.randomUUID());
            unit.setType(OrganizationalUnitType.TEAM);
            unit = units.saveAndFlush(unit);
            Position position = new Position();
            position.setName("Analyst " + UUID.randomUUID());
            position.setLevel("L2");
            position = positions.saveAndFlush(position);

            String login = "leave-" + UUID.randomUUID().toString().substring(0, 8);
            Employee e = new Employee();
            e.setLogin(login);
            e.setEmail(login + "@example.com");
            e.setPassword("x".repeat(60));
            e.setActivated(true);
            e.setFirstName("Camille");
            e.setLastName("Martin");
            e.setStatus(EmployeeStatus.ACTIVE);
            e.setStartDate(LocalDate.of(2025, 1, 1));
            e.setBirthDate(LocalDate.of(1985, 4, 12));
            // No payroll template covers France, so only leave and pay show in the result.
            e.setCountry(country(CountryCode.FR));
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
            c.setName("Leave monthly " + UUID.randomUUID());
            c.setFrequency(Frequency.MONTHLY);
            c.setTimezone(java.util.TimeZone.getTimeZone("Europe/Paris"));
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
            for (PayComponentCode code : List.of(PayComponentCode.DEDUCTION, PayComponentCode.TAX_PIT, PayComponentCode.EMPLOYER_CHARGE)) {
                if (components.findAll().stream().noneMatch(pc -> pc.getCode() == code)) {
                    // created by a leave pay rule or a template; removed afterwards so other tests see the original setup
                    cleanup.add(() -> components.findAll().stream().filter(pc -> pc.getCode() == code).forEach(components::delete));
                }
            }
        });
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(s -> {
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
            leaveRequests.deleteAll(
                leaveRequests.findAll(
                    (Specification<LeaveRequest>) (root, q, cb) -> cb.equal(root.get("employee").get("id"), employee.getId())
                )
            );
            leaveRules.deleteAll();
            compensations.deleteAll(
                compensations.findAll().stream().filter(c -> c.getEmployee().getId().equals(employee.getId())).toList()
            );
            for (String templateId : List.of(BG_TEMPLATE, ES_TEMPLATE, HU_TEMPLATE, AL_TEMPLATE, EE_TEMPLATE, HR_TEMPLATE, PL_TEMPLATE)) {
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
    void aWholeMonthOfUnpaidLeaveDeductsExactlyTheBaseSalary() {
        rule(LeaveType.UNPAID, "100", List.of());
        // March 2026 has 31 days and 22 working days; November 2026 has 30 days and 21 working days.
        leave(LeaveType.UNPAID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        leave(LeaveType.UNPAID, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        for (PayrollPeriod month : List.of(month(2026, 3), month(2026, 11))) {
            PayrollResult result = resultOf(calculate(month).id());

            List<PayrollLine> leaveLines = leaveLines(result);
            assertThat(leaveLines).hasSize(1);
            // Calendar days over approximate working days deducted 31/22 of the salary before.
            assertThat(leaveLines.get(0).getAmount()).isEqualByComparingTo("2000.00");
            assertThat(leaveLines.get(0).displayName()).isEqualTo(
                "Unpaid leave: " + leaveLines.get(0).getQuantity().intValue() + " days unpaid"
            );
            assertThat(result.getGross()).isEqualByComparingTo("2000.00");
            assertThat(result.getNet()).isEqualByComparingTo("0.00");
        }
    }

    @Test
    void weekendsInALeaveAreNotDeducted() {
        rule(LeaveType.UNPAID, "100", List.of());
        // Friday 6 to Monday 9 March 2026: two working days of 22.
        leave(LeaveType.UNPAID, LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 9));

        PayrollLine line = leaveLines(resultOf(calculate(month(2026, 3)).id())).get(0);

        assertThat(line.getQuantity()).isEqualByComparingTo("2");
        assertThat(line.getAmount()).isEqualByComparingTo("181.82"); // 2000 × 2 / 22
        assertThat(line.displayName()).isEqualTo("Unpaid leave: 2 days unpaid");
    }

    @Test
    void parentalLeaveIsPaidAtTheRulesRate() {
        rule(LeaveType.MATERNITY, "30", List.of());
        // Wednesday 1 to Friday 10 April 2026: 8 working days of 22, paid at 70%.
        leave(LeaveType.MATERNITY, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 10));

        PayrollResult result = resultOf(calculate(month(2026, 4)).id());

        List<PayrollLine> leaveLines = leaveLines(result);
        assertThat(leaveLines).hasSize(1);
        assertThat(leaveLines.get(0).getAmount()).isEqualByComparingTo("218.18"); // 2000 × 8 × 30% / 22
        assertThat(leaveLines.get(0).displayName()).isEqualTo("Parental leave: 8 days at 70% pay");
        assertThat(result.getNet()).isEqualByComparingTo("1781.82");
    }

    @Test
    void sickPayBandsCountFromTheAbsencesFirstDayAcrossPeriods() {
        // Six weeks' sick pay from the employer, then nothing (the health insurer pays).
        rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, 42, BigDecimal.ZERO)));
        // Day 1 is Monday 2 March; day 42 is Sunday 12 April.
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 4, 30));

        PayrollResult march = resultOf(calculate(month(2026, 3)).id());
        PayrollResult april = resultOf(calculate(month(2026, 4)).id());

        assertThat(leaveLines(march)).isEmpty();
        assertThat(march.getNet()).isEqualByComparingTo("2000.00");
        List<PayrollLine> aprilLines = leaveLines(april);
        assertThat(aprilLines).hasSize(1);
        // 13 to 30 April, days 43 to 60 of the absence: 14 of April's 22 working days.
        assertThat(aprilLines.get(0).getQuantity()).isEqualByComparingTo("14");
        assertThat(aprilLines.get(0).getAmount()).isEqualByComparingTo("1272.73");
        assertThat(aprilLines.get(0).getExplain()).contains("days 43–60 of the absence");
        assertThat(aprilLines.get(0).displayName()).isEqualTo("Sick leave: 14 days unpaid");
    }

    @Test
    void aSickNoteExtendedOverAWeekendContinuesTheAbsence() {
        rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, 42, BigDecimal.ZERO)));
        // The first note ends on Friday 27 March, the next starts on Monday 30 March.
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 27));
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 30));

        List<PayrollLine> april = leaveLines(resultOf(calculate(month(2026, 4)).id()));

        assertThat(april).hasSize(1);
        assertThat(april.get(0).getAmount()).isEqualByComparingTo("1272.73");
    }

    @Test
    void aSickNoteAfterAWorkingDayBackStartsANewAbsence() {
        rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, 42, BigDecimal.ZERO)));
        // Back at work on Monday 30 March, sick again from Tuesday 31 March: April is within six weeks.
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 27));
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));

        assertThat(leaveLines(resultOf(calculate(month(2026, 4)).id()))).isEmpty();
    }

    @Test
    void bandsAtSeveralRatesGiveALinePerRate() {
        // Unpaid waiting day, then 50% for days 2-10, then fully paid.
        rule(LeaveType.SICK, "0", List.of(new LeavePayBand(1, 1, new BigDecimal("100")), new LeavePayBand(2, 10, new BigDecimal("50"))));
        // Monday 2 to Friday 20 March 2026: day 1 unpaid, days 2-10 (Tue 3 - Wed 11 March, 7 working days) at 50%.
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 20));

        List<PayrollLine> leaveLines = leaveLines(resultOf(calculate(month(2026, 3)).id()));

        assertThat(leaveLines)
            .extracting(PayrollLine::displayName, l -> l.getAmount().toPlainString())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("Sick leave: 1 day unpaid", "90.91"),
                org.assertj.core.groups.Tuple.tuple("Sick leave: 7 days at 50% pay", "318.18")
            );
    }

    @Test
    void leaveWithoutARuleIsPaidInFull() {
        rule(LeaveType.UNPAID, "100", List.of());
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 13));

        PayrollResult result = resultOf(calculate(month(2026, 3)).id());

        assertThat(leaveLines(result)).isEmpty();
        assertThat(result.getNet()).isEqualByComparingTo("2000.00");
    }

    @Test
    void overlappingBandsAreRefused() {
        assertThatThrownBy(() ->
            rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, 10, BigDecimal.ZERO), new LeavePayBand(10, 20, new BigDecimal("50"))))
        )
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("overlap on day 10");
        assertThatThrownBy(() ->
            rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, null, BigDecimal.ZERO), new LeavePayBand(30, 40, new BigDecimal("50"))))
        )
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Only the last pay band");
    }

    @Test
    void aRuleIsPreTaxByDefaultAndItsBandsCanBeChanged() {
        LeaveTypeRuleResponse created = rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, 42, BigDecimal.ZERO)));

        assertThat(created.affectsTaxableSalary()).isTrue();
        assertThat(created.description()).isEqualTo("SICK - by pay band to day 42, then Unpaid");
        LeaveTypeRuleResponse updated = leaveTypeRuleService.updateRule(
            created.id(),
            new UpdateLeaveTypeRuleRequest(null, List.of(new LeavePayBand(1, 3, new BigDecimal("100"))), null)
        );
        assertThat(updated.payBands()).containsExactly(new LeavePayBand(1, 3, new BigDecimal("100")));
        BigDecimal afterTheBand = tx.execute(s -> leaveRules.findById(created.id()).orElseThrow().deductionPercentageOnDay(4));
        assertThat(afterTheBand).isEqualByComparingTo("100");
    }

    @Test
    void aTemplateTakesLeaveDeductionsOffItsBases() {
        moveEmployeeTo(CountryCode.BG);
        templateService.configure(BG_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("kidCode", "62")));
        templateService.saveProfile(BG_TEMPLATE, employee.getId(), Map.of("qualificationGroup", "2"));
        rule(LeaveType.UNPAID, "100", List.of());
        // Monday 2 to Friday 6 March 2026: 5 working days of 22.
        leave(LeaveType.UNPAID, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        List<PayrollLine> resultLines = linesOf(resultOf(run.id()));
        assertThat(resultLines)
            .filteredOn(l -> l.getLineCategory() == PayrollLineCategory.LEAVE_DEDUCTION)
            .singleElement()
            .satisfies(l -> assertThat(l.getAmount()).isEqualByComparingTo("454.55"));
        assertThat(resultLines)
            .filteredOn(l -> "pension".equals(l.getTemplateStep()) && l.getLineCategory() == PayrollLineCategory.WITHHOLDING)
            .singleElement()
            .satisfies(l -> assertThat(l.getQuantity()).isEqualByComparingTo("1545.45")); // template lines keep their base as the quantity
    }

    @Test
    void aTemplateThatCantHandleLeaveDeductionsRefusesTheEmployee() {
        spanishEmployee();
        rule(LeaveType.SICK, "40", List.of());
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse run = calculate(month(2026, 3));

        // Spanish temporary incapacity contributes on the previous month's base, which the template doesn't model.
        assertThat(run.validationErrors())
            .filteredOn(err -> employee.getId().equals(err.employeeId()))
            .singleElement()
            .satisfies(err -> assertThat(err.message()).startsWith("Unpaid, sick and birth leave are not supported"));
    }

    @Test
    void fullyPaidSickDaysStillReachTheTemplateAsSickPay() {
        spanishEmployee();
        // Fully paid for six weeks: nothing is deducted, but the days are still paid sick leave.
        rule(LeaveType.SICK, "100", List.of(new LeavePayBand(1, 42, BigDecimal.ZERO)));
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(leaveLines(resultOfOrNull(run.id()))).isEmpty();
        assertThat(run.validationErrors())
            .filteredOn(err -> employee.getId().equals(err.employeeId()))
            .singleElement()
            .satisfies(err -> assertThat(err.message()).startsWith("Unpaid, sick and birth leave are not supported"));
    }

    private void spanishEmployee() {
        moveEmployeeTo(CountryCode.ES);
        templateService.configure(ES_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("accidentRate", "0.015")));
        templateService.saveProfile(
            ES_TEMPLATE,
            employee.getId(),
            Map.of(
                "annualPay",
                "24000.00",
                "annualSocialContributions",
                "1560.00",
                "familySituation",
                "3",
                "withholdingContract",
                "GENERAL",
                "contributionGroup",
                "5",
                "unemploymentRate",
                "INDEFINITE",
                "taxTerritory",
                "COMMON"
            )
        );
    }

    @Test
    void sickLeaveWithoutARuleCalculatesAsBefore() {
        moveEmployeeTo(CountryCode.HU);
        payInHuf("400000");
        templateService.configure(HU_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        PayrollRunResponse before = calculate(month(2026, 3));
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse after = calculate(month(2026, 3));

        // No leave pay rule: the sick days are paid in full and the template sees an ordinary month.
        assertThat(after.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        assertThat(templateAmounts(after)).isNotEmpty().isEqualTo(templateAmounts(before));
        assertThat(resultOf(after.id()).getNet()).isEqualByComparingTo(resultOf(before.id()).getNet());
    }

    @Test
    void hungarianSickPayIsTaxedAsWage() {
        moveEmployeeTo(CountryCode.HU);
        payInHuf("400000");
        templateService.configure(HU_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        // Betegszabadság: the employer pays 70% (Tbj. 27. § (1) a): part of the contribution base).
        rule(LeaveType.SICK, "30", List.of());
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        PayrollResult result = resultOf(run.id());
        // 400,000 x 5 x 30% / 22 = 27,272.73 not paid; tb-járulék 18.5% on the rest, 372,727.27 -> 68,955.
        assertThat(leaveLines(result)).singleElement().satisfies(l -> assertThat(l.getAmount()).isEqualByComparingTo("27272.73"));
        assertThat(templateAmounts(run)).contains("socialSecurity/WITHHOLDING=68955");
    }

    @Test
    void aTemplateThatDoesNotModelParentalPayRefusesIt() {
        moveEmployeeTo(CountryCode.HU);
        payInHuf("400000");
        templateService.configure(HU_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        rule(LeaveType.MATERNITY, "30", List.of());
        leave(LeaveType.MATERNITY, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors())
            .filteredOn(err -> employee.getId().equals(err.employeeId()))
            .singleElement()
            .satisfies(err -> assertThat(err.message()).startsWith("Paid parental leave is not supported"));
    }

    @Test
    void anAlbanianTemplatePaysStatutorySickPayWithoutAWorkspaceRule() {
        albanianEmployee();
        // Days 1-14 at 80% (employer), from day 15 unpaid (social insurance): Monday 2 to Friday 27 March.
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 27));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        assertThat(leaveLines(resultOf(run.id())))
            .extracting(PayrollLine::displayName, l -> l.getAmount().toPlainString())
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("Sick leave: 10 days at 80% pay", "18181.82"),
                org.assertj.core.groups.Tuple.tuple("Sick leave: 10 days unpaid", "90909.09")
            );
        // The page lists the statutory rule as the template's.
        assertThat(leaveTypeRuleService.getAllRules())
            .filteredOn(r -> "AL".equals(r.countryCode()))
            .singleElement()
            .satisfies(r -> {
                assertThat(r.origin()).isEqualTo("TEMPLATE");
                assertThat(r.templateId()).isEqualTo(AL_TEMPLATE);
                assertThat(r.id()).isNull();
            });
    }

    @Test
    void aTemplateShowsWhichLeaveTypesHaveNoRuleAndArePaidInFull() {
        leaveTypeRuleService.createRule(
            new CreateLeaveTypeRuleRequest(country(CountryCode.EE).getId(), LeaveType.UNPAID, new BigDecimal("100"), List.of(), null)
        );

        // Estonia declares statutory sick pay; parental leave has no rule, so payroll would pay it in full.
        assertThat(templateService.get(EE_TEMPLATE).leavePay())
            .extracting(PayrollTemplateResponse.LeaveCoverage::leaveType, PayrollTemplateResponse.LeaveCoverage::rule)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple(LeaveType.SICK, "TEMPLATE"),
                org.assertj.core.groups.Tuple.tuple(LeaveType.MATERNITY, "NONE"),
                org.assertj.core.groups.Tuple.tuple(LeaveType.UNPAID, "WORKSPACE")
            );
        assertThat(templateService.get(EE_TEMPLATE).leavePay().get(0).note()).contains("Tervisekassa");

        // Enabling the template says so too.
        assertThat(templateService.configure(EE_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of())).warnings()).anySatisfy(w ->
            assertThat(w).startsWith("No leave pay rule for parental leave in EE")
        );
    }

    @Test
    void anAlbanianMonthWithSicknessBenefitIsInsuredOnThePayForTheDaysWorked() {
        albanianEmployee();
        payIn("100000", CurrencyCode.ALL);
        // From day 15 social insurance pays: the month's pay falls below the 50,000 minimum wage, and VKM 77/2015 IV.6
        // puts contributions on the pay for the days worked instead of refusing the month.
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 27));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        // 100,000 - 9,090.91 - 45,454.55 = 45,454.54 paid; 9.5% = 4,318.18.
        assertThat(templateAmounts(run)).contains("socialInsurance/WITHHOLDING=4318.18");
    }

    @Test
    void anEstonianLeavingMonthProratesTheSocialTaxMinimum() {
        moveEmployeeTo(CountryCode.EE);
        payIn("500", CurrencyCode.EUR);
        tx.executeWithoutResult(s -> {
            Employee e = employees.findById(employee.getId()).orElseThrow();
            e.setEndDate(LocalDate.of(2026, 6, 12));
            employees.save(e);
        });
        templateService.configure(EE_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        templateService.saveProfile(EE_TEMPLATE, employee.getId(), Map.of("fundedPensionRate", "NONE", "taxFreeAmount", "700"));

        PayrollRunResponse run = calculate(month(2026, 6));

        // määrus nr 17 § 6 lg 2: 12 days / 30 x 886 = 354.40 < 500 paid, so social tax on the pay; before, refused.
        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        assertThat(templateAmounts(run)).contains("socialTax/EMPLOYER_CHARGE=165");
    }

    @Test
    void croatianSickLeavePastDay42IsRefused() {
        moveEmployeeTo(CountryCode.HR);
        payIn("2000", CurrencyCode.EUR);
        templateService.configure(HR_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        templateService.saveProfile(
            HR_TEMPLATE,
            employee.getId(),
            Map.of("municipalityCode", "1333", "personalAllowanceCoefficient", "1", "secondPillarMember", "true")
        );
        rule(LeaveType.SICK, "30", List.of());
        // From 19 January: day 42 is 1 March, so March reaches HZZO's days.
        leave(LeaveType.SICK, LocalDate.of(2026, 1, 19), LocalDate.of(2026, 3, 13));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors())
            .filteredOn(err -> employee.getId().equals(err.employeeId()))
            .singleElement()
            .satisfies(err -> assertThat(err.message()).startsWith("Sick leave past day 42 is paid on HZZO's account"));
    }

    @Test
    void polishSickPayIsOutsideZusButInHealthAndTax() {
        moveEmployeeTo(CountryCode.PL);
        payIn("6000", CurrencyCode.PLN);
        templateService.configure(PL_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of("accidentRate", "0.0167")));
        templateService.saveProfile(PL_TEMPLATE, employee.getId(), Map.of("pit2Reduction", "ONE_TWELFTH", "labourFundPartialMonth", "DUE"));
        rule(LeaveType.SICK, "20", List.of());
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(run.validationErrors()).filteredOn(err -> employee.getId().equals(err.employeeId())).isEmpty();
        // 6,000 - 272.73 unpaid - 1,090.91 sick pay = 4,636.36 for ZUS; pension 9.76% = 452.51.
        assertThat(templateAmounts(run)).contains("pension/WITHHOLDING=452.51");
    }

    @Test
    void aWorkspaceRuleOverridesTheTemplatesStatutoryRule() {
        albanianEmployee();
        // The collective agreement pays sick days in full.
        rule(LeaveType.SICK, "0", List.of());
        leave(LeaveType.SICK, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 27));

        PayrollRunResponse run = calculate(month(2026, 3));

        assertThat(leaveLines(resultOf(run.id()))).isEmpty();
        assertThat(leaveTypeRuleService.getAllRules())
            .filteredOn(r -> "AL".equals(r.countryCode()))
            .singleElement()
            .satisfies(r -> {
                assertThat(r.origin()).isEqualTo("WORKSPACE");
                assertThat(r.overridesTemplate()).isTrue();
            });
    }

    @Test
    void usingATemplateSetsTheWorkspaceCurrency() {
        // Recorded at sign-up (not enabled yet): the workspace starts in the template's currency.
        templateService.configure(AL_TEMPLATE, new ConfigurePayrollTemplateRequest(false, Map.of()));
        assertThat(defaultCurrency()).isEqualTo(CurrencyCode.ALL);

        // An administrator changes it; saving the template's settings again keeps their choice.
        setDefaultCurrency(CurrencyCode.EUR);
        templateService.configure(AL_TEMPLATE, new ConfigurePayrollTemplateRequest(false, Map.of()));
        assertThat(defaultCurrency()).isEqualTo(CurrencyCode.EUR);

        // Switching the template on is using it: its currency again.
        templateService.configure(AL_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        assertThat(defaultCurrency()).isEqualTo(CurrencyCode.ALL);
        setDefaultCurrency(CurrencyCode.EUR);
        templateService.configure(AL_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        assertThat(defaultCurrency()).isEqualTo(CurrencyCode.EUR);
    }

    private void albanianEmployee() {
        moveEmployeeTo(CountryCode.AL);
        payIn("200000", CurrencyCode.ALL);
        templateService.configure(AL_TEMPLATE, new ConfigurePayrollTemplateRequest(true, Map.of()));
        templateService.saveProfile(AL_TEMPLATE, employee.getId(), Map.of("personalStatusDeclaration", "SIGNED_WITH_THIS_EMPLOYER"));
    }

    private CurrencyCode defaultCurrency() {
        return tx.execute(s -> organizationSettings.findAll().stream().findFirst().orElseThrow().getDefaultCurrency().getCode());
    }

    private void setDefaultCurrency(CurrencyCode code) {
        tx.executeWithoutResult(s -> {
            com.humano.domain.payroll.OrganizationSettings o = organizationSettings.findAll().stream().findFirst().orElseThrow();
            o.setDefaultCurrency(currencies.findByCode(code).orElseThrow());
            organizationSettings.save(o);
        });
    }

    private LeaveTypeRuleResponse rule(LeaveType type, String deductionPercentage, List<LeavePayBand> bands) {
        return leaveTypeRuleService.createRule(
            new CreateLeaveTypeRuleRequest(employeeCountryId(), type, new BigDecimal(deductionPercentage), bands, null)
        );
    }

    private void leave(LeaveType type, LocalDate start, LocalDate end) {
        tx.executeWithoutResult(s -> {
            LeaveRequest r = new LeaveRequest();
            r.setEmployee(employees.findById(employee.getId()).orElseThrow());
            r.setLeaveType(type);
            r.setStartDate(start);
            r.setEndDate(end);
            r.setStatus(LeaveStatus.APPROVED);
            r.setReason("Approved absence for the payroll test");
            leaveRequests.saveAndFlush(r);
        });
    }

    private void moveEmployeeTo(CountryCode code) {
        tx.executeWithoutResult(s -> {
            Employee e = employees.findById(employee.getId()).orElseThrow();
            e.setCountry(country(code));
            employees.save(e);
        });
    }

    private void payInHuf(String amount) {
        payIn(amount, CurrencyCode.HUF);
    }

    private void payIn(String amount, CurrencyCode currency) {
        tx.executeWithoutResult(s ->
            compensations
                .findAll()
                .stream()
                .filter(c -> c.getEmployee().getId().equals(employee.getId()))
                .forEach(c -> {
                    c.setBaseAmount(new BigDecimal(amount));
                    c.setCurrency(currencies.findByCode(currency).orElseThrow());
                    compensations.save(c);
                })
        );
    }

    /** The template lines of our employee's result, as step/category=amount. */
    private List<String> templateAmounts(PayrollRunResponse run) {
        return linesOf(resultOf(run.id()))
            .stream()
            .filter(l -> l.getTemplateRef() != null)
            .map(l -> l.getTemplateStep() + "/" + l.getLineCategory() + "=" + l.getAmount().stripTrailingZeros().toPlainString())
            .sorted()
            .toList();
    }

    private UUID employeeCountryId() {
        return tx.execute(s -> employees.findById(employee.getId()).orElseThrow().getCountry().getId());
    }

    private Country country(CountryCode code) {
        return countries.findAll().stream().filter(c -> c.getCode() == code).findFirst().orElseThrow();
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
            p.setCode("LV-" + start + "-" + UUID.randomUUID().toString().substring(0, 6));
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

    /** Our employee's result in the run, or null when the calculation refused them. */
    private PayrollResult resultOfOrNull(UUID runId) {
        return tx.execute(s ->
            results
                .findAll(
                    (Specification<PayrollResult>) (root, q, cb) ->
                        cb.and(cb.equal(root.get("run").get("id"), runId), cb.equal(root.get("employee").get("id"), employee.getId()))
                )
                .stream()
                .findFirst()
                .orElse(null)
        );
    }

    private List<PayrollLine> leaveLines(PayrollResult result) {
        if (result == null) return List.of();
        return linesOf(result)
            .stream()
            .filter(l -> l.getLineCategory() == PayrollLineCategory.LEAVE_DEDUCTION)
            .sorted(java.util.Comparator.comparing(PayrollLine::getSequence))
            .toList();
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
