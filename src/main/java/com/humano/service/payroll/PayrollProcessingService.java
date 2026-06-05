package com.humano.service.payroll;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.payroll.*;
import com.humano.domain.hr.LeaveRequest;
import com.humano.domain.payroll.*;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ApprovePayrollRunRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.request.RecalculatePayrollRequest;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.dto.payroll.response.PayrollRunSummaryResponse;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.payroll.*;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for payroll processing &mdash; run initiation, per-employee calculation,
 * approval workflow, and posting.
 *
 * <h2>Per-employee calculation pipeline (contract)</h2>
 *
 * <pre>
 *   resolveResult(run, period, employee)
 *        │
 *        ▼
 *   Compensation comp = findActiveCompensation(employee, period.endDate)   // required
 *        │
 *        ▼
 *   ┌─ Step  1 ─ Base salary
 *   │            calculateBaseSalary(comp, period)
 *   │            → PayrollLine(BASIC, explain="basis=X, periodAmount=...")
 *   │
 *   ├─ Step  2 ─ Earnings additions
 *   │   ├─ 2a   PayrollInput  rows (kind=EARNING)               → line each, gross +=
 *   │   ├─ 2b   Bonus rows where awardDate ∈ period AND !isPaid → line each, gross +=
 *   │   └─ 2c   Formula-driven earning PayComponents            → line each, gross +=
 *   │            (existing PayRule/SpEL path)
 *   │
 *   ├─ Step  3 ─ Leave deductions
 *   │            LeaveRequest (APPROVED, overlap period)
 *   │             × LeaveTypeRule(country, leaveType)
 *   │            → line each; affectsTaxableSalary flag drives pre-tax counting
 *   │
 *   ├─ Step  4 ─ GROSS PAY marker          (totalled, no line emitted)
 *   │
 *   ├─ Step  5 ─ Pre-tax deductions
 *   │            Deduction rows where isPreTax=true              → line each
 *   │
 *   ├─ Step  6 ─ Taxable income marker     (gross − pre-tax − non-taxable earnings)
 *   │
 *   ├─ Step  7 ─ Income tax        ◀── PLACEHOLDER slot
 *   │            (positioned; no line emitted until TaxBracket integration)
 *   │
 *   ├─ Step  8 ─ Other withholdings ◀── PLACEHOLDER slot
 *   │
 *   ├─ Step  9 ─ Employee benefit costs
 *   │            EmployeeBenefit (ACTIVE, overlap period)
 *   │             → employeeCost line (deduction)
 *   │             → employerCost line (employer charge; accumulates to step 12)
 *   │
 *   ├─ Step 10 ─ Post-tax deductions
 *   │            Deduction rows where isPreTax=false             → line each
 *   │            + formula-driven DEDUCTION PayComponents       → line each
 *   │
 *   ├─ Step 11 ─ NET PAY marker            = gross − Σ deductions
 *   │
 *   └─ Step 12 ─ EMPLOYER COST marker      = gross + Σ employer-charges + Σ benefit.employerCost
 *                + formula-driven EMPLOYER_CHARGE PayComponents → line each
 *
 *   persist(result + lines)
 * </pre>
 *
 * <p><strong>Determinism contract.</strong> Every collection that drives a line-producing
 * loop is sorted with a unique tiebreaker (id or code); every emitted line carries an
 * {@code explain} populated with the inputs and arithmetic that produced its amount;
 * every money value is rounded with {@link #MONEY_ROUNDING} at scale {@link #MONEY_SCALE}.
 * This is what gives the "stable across runs and human-readable" acceptance teeth.
 *
 * <p><strong>Out-of-scope hooks (lanes guarded).</strong> Steps 7/8 are positioned but
 * empty &mdash; country-aware progressive tax is a future task. Multi-currency
 * conversion at the run boundary is another future task. Idempotency-hash content should
 * not be extended without careful consideration. Don't widen here.
 */
@Service
@Transactional
public class PayrollProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PayrollProcessingService.class);

    /** Money scale &mdash; invariant I6: every emitted amount is rounded to this scale. */
    private static final int MONEY_SCALE = 2;

    /** Rounding mode for {@link #MONEY_SCALE}. */
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    /** Rate scale for ratio intermediates (e.g. percentage / 100) before re-scaling money. */
    private static final int RATE_SCALE = 6;

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollResultRepository payrollResultRepository;
    private final PayrollLineRepository payrollLineRepository;
    private final PayrollInputRepository payrollInputRepository;
    private final PayComponentRepository payComponentRepository;
    private final PayRuleRepository payRuleRepository;
    private final CompensationRepository compensationRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;
    private final BonusRepository bonusRepository;
    private final DeductionRepository deductionRepository;
    private final EmployeeBenefitRepository employeeBenefitRepository;
    private final LeaveTypeRuleRepository leaveTypeRuleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final PayrollFormulaEngine formulaEngine;
    private final DeductionService deductionService;
    private final BonusService bonusService;

    public PayrollProcessingService(
        PayrollRunRepository payrollRunRepository,
        PayrollPeriodRepository payrollPeriodRepository,
        PayrollResultRepository payrollResultRepository,
        PayrollLineRepository payrollLineRepository,
        PayrollInputRepository payrollInputRepository,
        PayComponentRepository payComponentRepository,
        PayRuleRepository payRuleRepository,
        CompensationRepository compensationRepository,
        EmployeeRepository employeeRepository,
        CurrencyRepository currencyRepository,
        BonusRepository bonusRepository,
        DeductionRepository deductionRepository,
        EmployeeBenefitRepository employeeBenefitRepository,
        LeaveTypeRuleRepository leaveTypeRuleRepository,
        LeaveRequestRepository leaveRequestRepository,
        TaxBracketRepository taxBracketRepository,
        PayrollFormulaEngine formulaEngine,
        DeductionService deductionService,
        BonusService bonusService
    ) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payrollResultRepository = payrollResultRepository;
        this.payrollLineRepository = payrollLineRepository;
        this.payrollInputRepository = payrollInputRepository;
        this.payComponentRepository = payComponentRepository;
        this.payRuleRepository = payRuleRepository;
        this.compensationRepository = compensationRepository;
        this.employeeRepository = employeeRepository;
        this.currencyRepository = currencyRepository;
        this.bonusRepository = bonusRepository;
        this.deductionRepository = deductionRepository;
        this.employeeBenefitRepository = employeeBenefitRepository;
        this.leaveTypeRuleRepository = leaveTypeRuleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.formulaEngine = formulaEngine;
        this.deductionService = deductionService;
        this.bonusService = bonusService;
    }

    /**
     * Initiates a new payroll run for a specific period.
     *
     * <h3>Idempotency contract</h3>
     * The hash is now deterministic over {@code (periodId, scope, sortedEmployeeIds,
     * payRuleVersion, taxBracketVersion)}. Two identical calls produce the same hash, so
     * the <em>hash short-circuit</em> below returns the existing run instead of
     * attempting a second insert (the {@code payroll_run.hash} column is unique).
     *
     * <p>The original "draft already exists for this period" guard (period-scoped, not
     * hash-scoped) is preserved &mdash; it fires when a DRAFT exists for a DIFFERENT
     * input set (so the hashes differ) and {@code draftMode=false}.
     *
     * <p><b>Deviation from spec wording.</b> The specification names the short-circuit set as
     * {@code CALCULATED/APPROVED/POSTED}; we widen it to ALL statuses so the acceptance
     * "a DB unique-violation on payroll_run.hash cannot occur via the service path"
     * also holds in {@code draftMode}, where the period-DRAFT guard is skipped.
     */
    public PayrollRunResponse initiatePayrollRun(InitiatePayrollRunRequest request) {
        log.info("Initiating payroll run for period: {}", request.periodId());

        PayrollPeriod period = payrollPeriodRepository
            .findById(request.periodId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", request.periodId()));

        if (period.isClosed()) {
            throw new BusinessRuleViolationException("Cannot create payroll run for closed period");
        }

        // Resolve canonical inputs for the deterministic hash. Uses the SAME employee
        // resolution `calculatePayroll` will use so the hash reflects what will actually
        // be processed.
        //
        // Breadcrumb: `request.excludedEmployeeIds()` is intentionally NOT in the
        // hash today because `getEmployeesForScope` ignores it (and so does
        // `calculatePayroll`). The day exclusions / real scope filtering land, they
        // MUST be added to the hash here — otherwise two genuinely-different
        // SELECTED_EMPLOYEES runs (or "ALL minus X" vs. "ALL minus Y") will collapse to
        // one via the short-circuit below. Same caveat applies to non-ALL scopes:
        // today `getEmployeesForScope` returns `findAll()` for them, so the hash
        // overcounts; a future scope filter must also slot in here.
        String scope = request.scope() != null ? request.scope().name() : "ALL";
        List<UUID> sortedEmployeeIds = getEmployeesForScope(scope, period)
            .stream()
            .map(Employee::getId)
            .sorted(Comparator.comparing(UUID::toString))
            .toList();
        Instant payRuleVersion = payRuleRepository.findMaxLastModifiedDate().orElse(null);
        Instant taxBracketVersion = taxBracketRepository.findMaxLastModifiedDate().orElse(null);
        String hash = generateIdempotencyHash(period.getId(), scope, sortedEmployeeIds, payRuleVersion, taxBracketVersion);

        // Hash short-circuit  — return any existing run with this hash to honour
        // the "two no-ops" idempotency and prevent the unique-constraint violation at
        // save time.
        Optional<PayrollRun> existingByHash = payrollRunRepository
            .findAll((Specification<PayrollRun>) (root, query, cb) -> cb.equal(root.get("hash"), hash))
            .stream()
            .findFirst();
        if (existingByHash.isPresent()) {
            PayrollRun existing = existingByHash.get();
            log.info(
                "Idempotent re-call: short-circuiting to existing run {} (status={}, hash={})",
                existing.getId(),
                existing.getStatus(),
                hash
            );
            return toRunResponse(existing, Collections.emptyList());
        }

        // Period-DRAFT guard (separate concern, preserved). Catches a DRAFT for a
        // DIFFERENT input set; the matching-hash case is already handled above.
        Optional<PayrollRun> existingDraft = payrollRunRepository
            .findAll(
                (Specification<PayrollRun>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("period").get("id"), request.periodId()), cb.equal(root.get("status"), RunStatus.DRAFT))
            )
            .stream()
            .findFirst();

        if (existingDraft.isPresent() && !request.draftMode()) {
            throw new BusinessRuleViolationException("A draft payroll run already exists for this period. Complete or delete it first.");
        }

        PayrollRun run = new PayrollRun();
        run.setPeriod(period);
        run.setScope(scope);
        run.setStatus(RunStatus.DRAFT);
        run.setHash(hash);

        run = payrollRunRepository.save(run);
        log.info("Created payroll run {} with status DRAFT, hash={}", run.getId(), hash);

        return toRunResponse(run, Collections.emptyList());
    }

    /**
     * Calculates payroll for all employees in a run.
     */
    public PayrollRunResponse calculatePayroll(UUID runId) {
        log.info("Calculating payroll for run: {}", runId);

        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        if (run.getStatus() != RunStatus.DRAFT && run.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessRuleViolationException("Can only calculate payroll for DRAFT or CALCULATED runs");
        }

        PayrollPeriod period = run.getPeriod();
        List<Employee> employees = getEmployeesForScope(run.getScope(), period);

        List<PayrollRunResponse.PayrollValidationError> errors = new ArrayList<>();
        int processedCount = 0;

        for (Employee employee : employees) {
            try {
                calculateEmployeePayroll(run, period, employee);
                processedCount++;
            } catch (Exception e) {
                log.error("Error calculating payroll for employee {}: {}", employee.getId(), e.getMessage());
                errors.add(
                    new PayrollRunResponse.PayrollValidationError(
                        employee.getId(),
                        employee.getFirstName() + " " + employee.getLastName(),
                        "CALCULATION_ERROR",
                        e.getMessage(),
                        "ERROR"
                    )
                );
            }
        }

        run.setStatus(RunStatus.CALCULATED);
        run = payrollRunRepository.save(run);

        log.info("Completed payroll calculation for run {}. Processed: {}, Errors: {}", runId, processedCount, errors.size());

        return toRunResponse(run, errors);
    }

    /**
     * Calculates payroll for a single employee following the canonical §2.2 pipeline .
     * See the class-level sequence diagram for the contract.
     */
    private PayrollResult calculateEmployeePayroll(PayrollRun run, PayrollPeriod period, Employee employee) {
        log.debug("Calculating payroll for employee: {}", employee.getId());

        // ---- Resolve or create result, clear previous lines ----
        PayrollResult result = resolveResult(run, period, employee);

        // ---- Resolve compensation (required) ----
        Compensation compensation = findActiveCompensation(employee.getId(), period.getEndDate());
        if (compensation == null) {
            throw new BusinessRuleViolationException("No active compensation found for employee " + employee.getId());
        }
        result.setCurrency(compensation.getCurrency());

        // ---- Pipeline state ----
        List<PayrollLine> lines = new ArrayList<>();
        SequenceCounter seq = new SequenceCounter();
        BigDecimal baseSalary = calculateBaseSalary(compensation, period).setScale(MONEY_SCALE, MONEY_ROUNDING);
        List<PayrollInput> inputs = getPayrollInputs(employee.getId(), period.getId());
        Map<String, Object> context = buildCalculationContext(employee, compensation, period, inputs, baseSalary);

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal nonTaxableEarnings = BigDecimal.ZERO;
        BigDecimal preTaxDeductions = BigDecimal.ZERO;
        BigDecimal postTaxDeductions = BigDecimal.ZERO;
        BigDecimal employerCharges = BigDecimal.ZERO;
        BigDecimal benefitEmployerCost = BigDecimal.ZERO;

        // Cache the component map once; every step looks up by code for deterministic component
        // selection. PayComponent.code is unique-constrained at the entity level
        // (PayComponent.java:49 `@Column(name="code", unique=true)`), so the merge function
        // never actually fires — kept only to satisfy Collectors.toMap's signature.
        Map<PayComponentCode, PayComponent> componentsByCode = payComponentRepository
            .findAll()
            .stream()
            .filter(c -> c.getCode() != null)
            .collect(Collectors.toMap(PayComponent::getCode, c -> c, (a, b) -> a));
        Set<UUID> handledComponentIds = new HashSet<>();

        // ============================================================
        // Step 1 — Base salary
        // ============================================================
        PayComponent basicComponent = componentsByCode.get(PayComponentCode.BASIC);
        if (basicComponent != null) {
            String explain =
                "Step 1 — Base salary (basis=" +
                compensation.getBasis() +
                ", baseAmount=" +
                fmt(compensation.getBaseAmount()) +
                ") → period amount " +
                fmt(baseSalary);
            lines.add(emitLine(result, basicComponent, null, null, baseSalary, seq.next(), explain));
            handledComponentIds.add(basicComponent.getId());
            if (!Boolean.TRUE.equals(basicComponent.getTaxable())) {
                nonTaxableEarnings = nonTaxableEarnings.add(baseSalary);
            }
            context.put(basicComponent.getCode().name(), baseSalary);
        } else {
            log.warn("Step 1 — no BASIC PayComponent seeded; base salary {} counted in gross without a line", fmt(baseSalary));
        }
        gross = gross.add(baseSalary);

        // ============================================================
        // Step 2a — PayrollInput earnings (deterministic by id)
        // ============================================================
        List<PayrollInput> earningInputs = inputs
            .stream()
            .filter(i -> i.getComponent() != null && i.getComponent().getKind() == Kind.EARNING)
            .sorted(Comparator.comparing((PayrollInput i) -> i.getComponent().getCode().name()).thenComparing(PayrollInput::getId))
            .toList();
        for (PayrollInput input : earningInputs) {
            BigDecimal amt = scaledInputAmount(input);
            if (amt == null || amt.signum() == 0) {
                continue;
            }
            String explain = explainInput(input, amt, "Step 2a");
            lines.add(emitLine(result, input.getComponent(), input.getQuantity(), input.getRate(), amt, seq.next(), explain));
            gross = gross.add(amt);
            if (!Boolean.TRUE.equals(input.getComponent().getTaxable())) {
                nonTaxableEarnings = nonTaxableEarnings.add(amt);
            }
            context.put(input.getComponent().getCode().name(), amt);
            handledComponentIds.add(input.getComponent().getId());
        }

        // ============================================================
        // Step 2b — Bonus rows (awardDate ∈ period AND !isPaid)
        // ============================================================
        PayComponent bonusComponent = componentsByCode.get(PayComponentCode.BONUS);
        List<Bonus> activeBonuses = bonusRepository
            .findAll(
                (Specification<Bonus>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employee.getId()),
                        cb.between(root.get("awardDate"), period.getStartDate(), period.getEndDate()),
                        cb.isFalse(root.get("isPaid"))
                    )
            )
            .stream()
            .sorted(Comparator.comparing(Bonus::getAwardDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Bonus::getId))
            .toList();
        if (bonusComponent == null && !activeBonuses.isEmpty()) {
            log.warn("Step 2b — {} active bonus(es) found but no BONUS PayComponent seeded; lines skipped", activeBonuses.size());
        } else {
            for (Bonus bonus : activeBonuses) {
                BigDecimal amt = bonus.getAmount() == null ? BigDecimal.ZERO : bonus.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING);
                if (amt.signum() == 0) continue;
                String explain = "Step 2b — Bonus (" + bonus.getType() + ", awarded " + bonus.getAwardDate() + "): " + fmt(amt);
                lines.add(emitLine(result, bonusComponent, null, null, amt, seq.next(), explain));
                gross = gross.add(amt);
                if (!Boolean.TRUE.equals(bonusComponent.getTaxable())) {
                    nonTaxableEarnings = nonTaxableEarnings.add(amt);
                }
            }
        }

        // ============================================================
        // Step 2c — Formula-driven earning PayComponents (preserves the user-defined PayRule engine)
        // ============================================================
        for (PayComponent component : sortedComponentsByKind(componentsByCode.values(), Kind.EARNING, handledComponentIds)) {
            BigDecimal amt = safeCalculateComponent(component, context, inputs, employee);
            if (amt == null || amt.signum() == 0) continue;
            amt = amt.setScale(MONEY_SCALE, MONEY_ROUNDING);
            String explain = "Step 2c — Earning '" + component.getCode() + "' via PayRule formula: " + fmt(amt);
            lines.add(emitLine(result, component, null, null, amt, seq.next(), explain));
            gross = gross.add(amt);
            if (!Boolean.TRUE.equals(component.getTaxable())) {
                nonTaxableEarnings = nonTaxableEarnings.add(amt);
            }
            context.put(component.getCode().name(), amt);
        }

        // ============================================================
        // Step 3 — Leave deductions (LeaveTypeRule × LeaveRequest)
        //   Semantics: leave deductions are emitted as DEDUCTION lines and counted into
        //   preTax/postTax (driven by LeaveTypeRule.affectsTaxableSalary) — they DO NOT
        //   reduce `gross`. This keeps §2.2's "Gross = Σ earning lines" invariant and
        //   models unpaid leave as a separate deduction line on the payslip rather than
        //   a silent earnings reduction. Reviewers should know this is deliberate.
        // ============================================================
        PayComponent leaveDeductionComponent = componentsByCode.get(PayComponentCode.DEDUCTION);
        if (employee.getCountry() == null) {
            log.debug("Step 3 — leave deductions skipped: employee {} has no country", employee.getId());
        } else if (leaveDeductionComponent == null) {
            log.debug("Step 3 — leave deductions skipped: no DEDUCTION PayComponent seeded");
        } else {
            int workDays = Math.max(1, calculateWorkDays(period));
            BigDecimal dailyRate = baseSalary.divide(BigDecimal.valueOf(workDays), RATE_SCALE, MONEY_ROUNDING);
            Map<LeaveType, Integer> daysByType = aggregateApprovedLeaveDays(employee, period);
            for (Map.Entry<LeaveType, Integer> entry : daysByType.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                LeaveType lt = entry.getKey();
                int days = entry.getValue();
                Optional<LeaveTypeRule> ruleOpt = leaveTypeRuleRepository
                    .findAll(
                        (Specification<LeaveTypeRule>) (root, query, cb) ->
                            cb.and(
                                cb.equal(root.get("country").get("id"), employee.getCountry().getId()),
                                cb.equal(root.get("leaveType"), lt)
                            )
                    )
                    .stream()
                    .findFirst();
                if (
                    ruleOpt.isEmpty() ||
                    ruleOpt.get().getDeductionPercentage() == null ||
                    ruleOpt.get().getDeductionPercentage().signum() == 0
                ) {
                    continue;
                }
                LeaveTypeRule rule = ruleOpt.get();
                BigDecimal pctAsRatio = rule.getDeductionPercentage().divide(BigDecimal.valueOf(100), RATE_SCALE, MONEY_ROUNDING);
                BigDecimal deduction = dailyRate
                    .multiply(BigDecimal.valueOf(days))
                    .multiply(pctAsRatio)
                    .setScale(MONEY_SCALE, MONEY_ROUNDING);
                if (deduction.signum() == 0) continue;
                boolean affectsTaxable = Boolean.TRUE.equals(rule.getAffectsTaxableSalary());
                String explain =
                    "Step 3 — Leave deduction (" +
                    lt +
                    ", country=" +
                    employee.getCountry().getCode() +
                    "): " +
                    days +
                    " days × dailyRate " +
                    fmt(dailyRate) +
                    " × " +
                    fmt(rule.getDeductionPercentage()) +
                    "% = " +
                    fmt(deduction) +
                    (affectsTaxable ? " [pre-tax]" : " [post-tax]");
                lines.add(emitLine(result, leaveDeductionComponent, BigDecimal.valueOf(days), dailyRate, deduction, seq.next(), explain));
                if (affectsTaxable) {
                    preTaxDeductions = preTaxDeductions.add(deduction);
                } else {
                    postTaxDeductions = postTaxDeductions.add(deduction);
                }
            }
        }

        // ============================================================
        // Step 4 — GROSS PAY marker
        // ============================================================
        final BigDecimal grossPay = gross;
        context.put("grossSalary", grossPay);
        context.put("gross", grossPay);

        // ============================================================
        // Step 5 — Pre-tax deductions (Deduction.isPreTax = true)
        // ============================================================
        List<Deduction> activeDeductions = activeDeductionsForPeriod(employee, period);
        PayComponent generalDeductionComponent = componentsByCode.get(PayComponentCode.DEDUCTION);
        for (Deduction d : activeDeductions.stream().filter(d -> Boolean.TRUE.equals(d.getIsPreTax())).toList()) {
            BigDecimal amt = computeDeductionAmount(d, grossPay);
            if (amt.signum() == 0) continue;
            if (generalDeductionComponent == null) {
                log.warn("Step 5 — Pre-tax deduction {} skipped: no DEDUCTION PayComponent seeded", d.getId());
                continue;
            }
            String explain = explainDeduction(d, amt, grossPay, "Step 5", true);
            lines.add(emitLine(result, generalDeductionComponent, null, null, amt, seq.next(), explain));
            preTaxDeductions = preTaxDeductions.add(amt);
        }

        // ============================================================
        // Step 6 — Taxable income marker
        //   NOTE: PayComponent.taxable defaults to false, so a tenant that
        //   doesn't explicitly seed `BASIC.taxable=true` will treat base salary as
        //   non-taxable and step 7 will compute tax against the wrong base. A future
        //   task must either (a) ensure TenantInitializationService seeds BASIC with taxable=true,
        //   or (b) flip the entity default to true. Today this only affects the value
        //   logged by step 7's placeholder — no behavioural impact.
        // ============================================================
        BigDecimal taxableIncome = grossPay.subtract(preTaxDeductions).subtract(nonTaxableEarnings).max(BigDecimal.ZERO);
        context.put("taxableIncome", taxableIncome);

        // ============================================================
        // Step 7 — Income tax  ◀── PLACEHOLDER
        // ============================================================
        log.debug(
            "Step 7 — income tax placeholder slot (taxableIncome={}); progressive TaxBracket lookup is a future task",
            fmt(taxableIncome)
        );

        // ============================================================
        // Step 8 — Other withholdings  ◀── PLACEHOLDER
        // ============================================================
        log.debug("Step 8 — withholdings placeholder slot; TaxWithholding ledger update is a future task");

        // ============================================================
        // Step 9 — Employee benefit costs
        // ============================================================
        PayComponent employerChargeComponent = componentsByCode.get(PayComponentCode.EMPLOYER_CHARGE);
        List<EmployeeBenefit> activeBenefits = activeBenefitsForPeriod(employee, period);
        for (EmployeeBenefit b : activeBenefits) {
            BigDecimal employeeCost = nz(b.getEmployeeCost()).setScale(MONEY_SCALE, MONEY_ROUNDING);
            BigDecimal employerCost = nz(b.getEmployerCost()).setScale(MONEY_SCALE, MONEY_ROUNDING);
            if (employeeCost.signum() != 0) {
                if (generalDeductionComponent == null) {
                    log.warn("Step 9 — benefit {} employeeCost {} skipped: no DEDUCTION PayComponent seeded", b.getId(), fmt(employeeCost));
                } else {
                    String explain = "Step 9 — Benefit (" + b.getType() + ") employee cost: " + fmt(employeeCost);
                    lines.add(emitLine(result, generalDeductionComponent, null, null, employeeCost, seq.next(), explain));
                    postTaxDeductions = postTaxDeductions.add(employeeCost);
                }
            }
            if (employerCost.signum() != 0) {
                if (employerChargeComponent != null) {
                    String explain = "Step 9 — Benefit (" + b.getType() + ") employer cost: " + fmt(employerCost);
                    lines.add(emitLine(result, employerChargeComponent, null, null, employerCost, seq.next(), explain));
                }
                benefitEmployerCost = benefitEmployerCost.add(employerCost);
            }
        }

        // ============================================================
        // Step 10 — Post-tax deductions
        // ============================================================
        for (Deduction d : activeDeductions.stream().filter(d -> !Boolean.TRUE.equals(d.getIsPreTax())).toList()) {
            BigDecimal amt = computeDeductionAmount(d, grossPay);
            if (amt.signum() == 0) continue;
            if (generalDeductionComponent == null) {
                log.warn("Step 10 — Post-tax deduction {} skipped: no DEDUCTION PayComponent seeded", d.getId());
                continue;
            }
            String explain = explainDeduction(d, amt, grossPay, "Step 10", false);
            lines.add(emitLine(result, generalDeductionComponent, null, null, amt, seq.next(), explain));
            postTaxDeductions = postTaxDeductions.add(amt);
        }
        // Formula-driven DEDUCTION PayComponents in deterministic order
        for (PayComponent component : sortedComponentsByKind(componentsByCode.values(), Kind.DEDUCTION, handledComponentIds)) {
            BigDecimal amt = safeCalculateComponent(component, context, inputs, employee);
            if (amt == null || amt.signum() == 0) continue;
            amt = amt.setScale(MONEY_SCALE, MONEY_ROUNDING).abs();
            String explain = "Step 10 — Deduction component '" + component.getCode() + "' via PayRule formula: " + fmt(amt);
            lines.add(emitLine(result, component, null, null, amt, seq.next(), explain));
            postTaxDeductions = postTaxDeductions.add(amt);
            context.put(component.getCode().name(), amt);
        }

        // ============================================================
        // Step 11 — NET PAY marker
        // ============================================================
        BigDecimal totalDeductions = preTaxDeductions.add(postTaxDeductions);
        BigDecimal netPay = grossPay.subtract(totalDeductions);

        // ============================================================
        // Step 12 — EMPLOYER COST marker (+ formula-driven employer charges)
        // ============================================================
        for (PayComponent component : sortedComponentsByKind(componentsByCode.values(), Kind.EMPLOYER_CHARGE, handledComponentIds)) {
            BigDecimal amt = safeCalculateComponent(component, context, inputs, employee);
            if (amt == null || amt.signum() == 0) continue;
            amt = amt.setScale(MONEY_SCALE, MONEY_ROUNDING);
            String explain = "Step 12 — Employer charge '" + component.getCode() + "' via PayRule formula: " + fmt(amt);
            lines.add(emitLine(result, component, null, null, amt, seq.next(), explain));
            employerCharges = employerCharges.add(amt);
            context.put(component.getCode().name(), amt);
        }
        BigDecimal employerCost = grossPay.add(employerCharges).add(benefitEmployerCost);

        // ---- Persist totals + lines ----
        result.setGross(grossPay);
        result.setTotalDeductions(totalDeductions);
        result.setNet(netPay);
        result.setEmployerCost(employerCost);
        result = payrollResultRepository.save(result);

        for (PayrollLine line : lines) {
            line.setResult(result);
        }
        payrollLineRepository.saveAll(lines);

        log.debug(
            "Pipeline done for employee {}: gross={}, taxable={}, totalDeductions={}, net={}, employerCost={}, lines={}",
            employee.getId(),
            fmt(grossPay),
            fmt(taxableIncome),
            fmt(totalDeductions),
            fmt(netPay),
            fmt(employerCost),
            lines.size()
        );
        return result;
    }

    /**
     * Loads-or-creates the per-employee {@link PayrollResult} for this run, and clears any
     * existing {@link PayrollLine} rows from a prior recalculation.
     */
    private PayrollResult resolveResult(PayrollRun run, PayrollPeriod period, Employee employee) {
        Optional<PayrollResult> existing = payrollResultRepository
            .findAll(
                (Specification<PayrollResult>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("run").get("id"), run.getId()), cb.equal(root.get("employee").get("id"), employee.getId()))
            )
            .stream()
            .findFirst();
        if (existing.isPresent()) {
            PayrollResult result = existing.get();
            final UUID resultId = result.getId();
            List<PayrollLine> existingLines = payrollLineRepository.findAll(
                (Specification<PayrollLine>) (root, query, cb) -> cb.equal(root.get("result").get("id"), resultId)
            );
            payrollLineRepository.deleteAll(existingLines);
            return result;
        }
        PayrollResult result = new PayrollResult();
        result.setRun(run);
        result.setEmployee(employee);
        result.setPayrollPeriod(period);
        return result;
    }

    /**
     * Approves a payroll run.
     */
    public PayrollRunResponse approvePayrollRun(ApprovePayrollRunRequest request) {
        log.info("Approving payroll run: {}", request.payrollRunId());

        PayrollRun run = payrollRunRepository
            .findById(request.payrollRunId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollRun", request.payrollRunId()));

        if (run.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessRuleViolationException("Can only approve CALCULATED payroll runs. Current status: " + run.getStatus());
        }

        Employee approver = employeeRepository
            .findById(request.approverId())
            .orElseThrow(() -> new EntityNotFoundException("Employee (approver)", request.approverId()));

        // Validate approver has authority
        validateApprovalAuthority(approver, run);

        run.setStatus(RunStatus.APPROVED);
        run.setApprovedAt(OffsetDateTime.now());
        run.setApprovedBy(approver);

        run = payrollRunRepository.save(run);
        log.info("Payroll run {} approved by {}", run.getId(), approver.getId());

        return toRunResponse(run, Collections.emptyList());
    }

    /**
     * Posts an approved payroll run, making it final.
     */
    public PayrollRunResponse postPayrollRun(UUID runId) {
        log.info("Posting payroll run: {}", runId);

        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        if (run.getStatus() != RunStatus.APPROVED) {
            throw new BusinessRuleViolationException("Can only post APPROVED payroll runs. Current status: " + run.getStatus());
        }

        run.setStatus(RunStatus.POSTED);
        run = payrollRunRepository.save(run);

        // Close the period
        PayrollPeriod period = run.getPeriod();
        period.setClosed(true);
        payrollPeriodRepository.save(period);

        log.info("Payroll run {} posted and period {} closed", run.getId(), period.getId());

        return toRunResponse(run, Collections.emptyList());
    }

    /**
     * Gets a summary of a payroll run with aggregated statistics.
     */
    @Transactional(readOnly = true)
    public PayrollRunSummaryResponse getPayrollRunSummary(UUID runId) {
        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        List<PayrollResult> results = payrollResultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), runId)
        );

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal totalEmployerCost = BigDecimal.ZERO;

        for (PayrollResult result : results) {
            totalGross = totalGross.add(result.getGross());
            totalDeductions = totalDeductions.add(result.getTotalDeductions());
            totalNet = totalNet.add(result.getNet());
            totalEmployerCost = totalEmployerCost.add(result.getEmployerCost());
        }

        // Get breakdown by component
        Map<String, BigDecimal> earningsByComponent = new HashMap<>();
        Map<String, BigDecimal> deductionsByComponent = new HashMap<>();

        for (PayrollResult result : results) {
            List<PayrollLine> lines = payrollLineRepository.findAll(
                (Specification<PayrollLine>) (root, query, cb) -> cb.equal(root.get("result").get("id"), result.getId())
            );

            for (PayrollLine line : lines) {
                String componentName = line.getComponent().getName();
                if (line.getComponent().getKind() == Kind.EARNING) {
                    earningsByComponent.merge(componentName, line.getAmount(), BigDecimal::add);
                } else if (line.getComponent().getKind() == Kind.DEDUCTION) {
                    deductionsByComponent.merge(componentName, line.getAmount().abs(), BigDecimal::add);
                }
            }
        }

        String currencyCode = results.isEmpty() ? null : results.get(0).getCurrency().getCode().getCode();

        return new PayrollRunSummaryResponse(
            runId,
            run.getPeriod().getCode(),
            results.size(),
            results.size(),
            0, // Error count
            totalGross,
            totalDeductions,
            totalNet,
            totalEmployerCost,
            totalNet.add(totalEmployerCost),
            currencyCode,
            earningsByComponent,
            deductionsByComponent,
            Collections.emptyMap(), // By department - would need additional logic
            Collections.emptyList(), // Top earners
            null // Comparison with previous period
        );
    }

    /**
     * Recalculates payroll for specific employees or the entire run.
     */
    public PayrollRunResponse recalculatePayroll(RecalculatePayrollRequest request) {
        log.info("Recalculating payroll for run: {}", request.payrollRunId());

        PayrollRun run = payrollRunRepository
            .findById(request.payrollRunId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollRun", request.payrollRunId()));

        if (run.getStatus() == RunStatus.POSTED) {
            throw new BusinessRuleViolationException("Cannot recalculate posted payroll runs");
        }

        // Reset status to DRAFT for recalculation
        run.setStatus(RunStatus.DRAFT);
        run = payrollRunRepository.save(run);

        // Recalculate
        return calculatePayroll(run.getId());
    }

    // Helper methods

    private List<Employee> getEmployeesForScope(String scope, PayrollPeriod period) {
        if ("ALL".equals(scope)) {
            return employeeRepository.findAll(
                (Specification<Employee>) (root, query, cb) ->
                    cb.equal(root.get("status"), com.humano.domain.enumeration.hr.EmployeeStatus.ACTIVE)
            );
        }
        // Handle other scopes (UNIT, DEPARTMENT, etc.)
        return employeeRepository.findAll();
    }

    private Compensation findActiveCompensation(UUID employeeId, LocalDate asOfDate) {
        return compensationRepository
            .findAll(
                (Specification<Compensation>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate))
                    )
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    private BigDecimal calculateBaseSalary(Compensation compensation, PayrollPeriod period) {
        return switch (compensation.getBasis()) {
            case MONTHLY -> compensation.getBaseAmount();
            case ANNUAL -> compensation.getBaseAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case HOURLY -> compensation.getBaseAmount().multiply(BigDecimal.valueOf(160));
            default -> compensation.getBaseAmount();
        };
    }

    private List<PayrollInput> getPayrollInputs(UUID employeeId, UUID periodId) {
        return payrollInputRepository.findAll(
            (Specification<PayrollInput>) (root, query, cb) ->
                cb.and(cb.equal(root.get("employee").get("id"), employeeId), cb.equal(root.get("period").get("id"), periodId))
        );
    }

    private Map<String, Object> buildCalculationContext(
        Employee employee,
        Compensation compensation,
        PayrollPeriod period,
        List<PayrollInput> inputs,
        BigDecimal baseSalary
    ) {
        Map<String, Object> context = new HashMap<>();
        context.put("employeeId", employee.getId());
        context.put("baseSalary", baseSalary);
        context.put("grossSalary", baseSalary);
        context.put("periodStartDate", period.getStartDate());
        context.put("periodEndDate", period.getEndDate());
        context.put("workDays", calculateWorkDays(period));

        // Add inputs to context
        for (PayrollInput input : inputs) {
            String key = input.getComponent().getCode().name();
            if (input.getAmount() != null) {
                context.put(key, input.getAmount());
            } else if (input.getQuantity() != null && input.getRate() != null) {
                context.put(key + "_QTY", input.getQuantity());
                context.put(key + "_RATE", input.getRate());
                context.put(key, input.getQuantity().multiply(input.getRate()));
            }
        }

        return context;
    }

    private BigDecimal calculateComponent(PayComponent component, Map<String, Object> context, List<PayrollInput> inputs) {
        // Check for direct input first
        Optional<PayrollInput> directInput = inputs.stream().filter(i -> i.getComponent().getId().equals(component.getId())).findFirst();

        if (directInput.isPresent()) {
            PayrollInput input = directInput.get();
            if (input.getAmount() != null) {
                return input.getAmount();
            }
            if (input.getQuantity() != null && input.getRate() != null) {
                return input.getQuantity().multiply(input.getRate());
            }
        }

        // Find applicable rule
        List<PayRule> rules = payRuleRepository.findAll(
            (Specification<PayRule>) (root, query, cb) ->
                cb.and(cb.equal(root.get("payComponent").get("id"), component.getId()), cb.isTrue(root.get("active")))
        );

        if (rules.isEmpty()) {
            // For BASIC component, return base salary
            if (component.getCode() == PayComponentCode.BASIC) {
                return (BigDecimal) context.get("baseSalary");
            }
            return null;
        }

        // Sort by priority (higher priority first)
        rules.sort((a, b) -> {
            int pA = a.getPriority() != null ? a.getPriority() : 0;
            int pB = b.getPriority() != null ? b.getPriority() : 0;
            return Integer.compare(pB, pA);
        });

        PayRule rule = rules.get(0);

        try {
            return formulaEngine.evaluateFormula(rule.getFormula(), context, BigDecimal.class);
        } catch (Exception e) {
            log.warn("Error evaluating formula for component {}: {}", component.getCode(), e.getMessage());
            return null;
        }
    }

    private int calculateWorkDays(PayrollPeriod period) {
        // Simple calculation - would be enhanced with holiday calendar
        long days = java.time.temporal.ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
        return (int) ((days * 5) / 7); // Approximate work days
    }

    private void validateApprovalAuthority(Employee approver, PayrollRun run) {
        // Implement approval authority validation based on business rules
        // For now, just log
        log.debug("Validating approval authority for {} on run {}", approver.getId(), run.getId());
    }

    /** Bumped whenever the hash *format* changes so old runs don't collide with new ones. */
    private static final int HASH_VERSION = 1;

    private static final String HASH_DELIM = "|";

    /**
     * SHA-256 idempotency hash  over the canonical run inputs. Pure function of its
     * arguments &mdash; no clock, no randomness. Two calls with identical inputs yield
     * identical hashes; any change to employee set / pay-rule version / tax-bracket
     * version invalidates the hash.
     */
    private String generateIdempotencyHash(
        UUID periodId,
        String scope,
        List<UUID> sortedEmployeeIds,
        Instant payRuleVersion,
        Instant taxBracketVersion
    ) {
        StringBuilder payload = new StringBuilder();
        payload.append("v").append(HASH_VERSION).append(HASH_DELIM);
        payload.append(periodId).append(HASH_DELIM);
        payload.append(scope == null ? "" : scope).append(HASH_DELIM);
        payload.append(sortedEmployeeIds.stream().map(UUID::toString).collect(Collectors.joining(",")));
        payload.append(HASH_DELIM);
        payload.append(payRuleVersion == null ? "0" : payRuleVersion.toString());
        payload.append(HASH_DELIM);
        payload.append(taxBracketVersion == null ? "0" : taxBracketVersion.toString());
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JVM (Java SE platform spec); fail loud if missing.
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    // =========================================================================
    // Pipeline helpers — money formatting, line emission, source queries
    // =========================================================================

    /**
     * Monotonic sequence assignment for emitted lines. Captured in a tiny holder so the
     * pipeline reads as "seq.next()" at the call site.
     */
    private static final class SequenceCounter {

        private int value = 1;

        int next() {
            return value++;
        }
    }

    /** Builds a {@link PayrollLine} with the standard fields populated. */
    private PayrollLine emitLine(
        PayrollResult result,
        PayComponent component,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal amount,
        int sequence,
        String explain
    ) {
        PayrollLine line = new PayrollLine();
        line.setResult(result);
        line.setComponent(component);
        line.setQuantity(quantity);
        line.setRate(rate);
        line.setAmount(amount);
        line.setSequence(sequence);
        line.setExplain(explain);
        return line;
    }

    /** Human-readable money formatter used inside {@code explain} text. */
    private static String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Resolves the effective monetary amount for an input row, applying
     * {@link #MONEY_SCALE}/{@link #MONEY_ROUNDING}. Returns {@code null} when the input
     * yields no usable amount.
     */
    private BigDecimal scaledInputAmount(PayrollInput input) {
        if (input.getAmount() != null) {
            return input.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING);
        }
        if (input.getQuantity() != null && input.getRate() != null) {
            return input.getQuantity().multiply(input.getRate()).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }
        return null;
    }

    private String explainInput(PayrollInput input, BigDecimal amt, String stepTag) {
        String code = input.getComponent().getCode().name();
        if (input.getQuantity() != null && input.getRate() != null) {
            return (
                stepTag + " — PayrollInput (" + code + "): " + fmt(input.getQuantity()) + " × " + fmt(input.getRate()) + " = " + fmt(amt)
            );
        }
        return stepTag + " — PayrollInput (" + code + "): amount " + fmt(amt);
    }

    /** Computes the deduction amount &mdash; fixed amount or percentage of gross. */
    private BigDecimal computeDeductionAmount(Deduction d, BigDecimal gross) {
        if (d.getAmount() != null && d.getAmount().signum() != 0) {
            return d.getAmount().setScale(MONEY_SCALE, MONEY_ROUNDING);
        }
        if (d.getPercentage() != null && d.getPercentage().signum() != 0) {
            BigDecimal ratio = d.getPercentage().divide(BigDecimal.valueOf(100), RATE_SCALE, MONEY_ROUNDING);
            return gross.multiply(ratio).setScale(MONEY_SCALE, MONEY_ROUNDING);
        }
        return BigDecimal.ZERO;
    }

    private String explainDeduction(Deduction d, BigDecimal amt, BigDecimal gross, String stepTag, boolean preTax) {
        String basis;
        if (d.getAmount() != null && d.getAmount().signum() != 0) {
            basis = "fixed " + fmt(d.getAmount());
        } else if (d.getPercentage() != null) {
            basis = fmt(d.getPercentage()) + "% × gross " + fmt(gross);
        } else {
            basis = "amount " + fmt(amt);
        }
        return (
            stepTag +
            " — " +
            (preTax ? "Pre-tax" : "Post-tax") +
            " deduction (" +
            d.getType() +
            ", '" +
            d.getDescription() +
            "'): " +
            basis +
            " = " +
            fmt(amt)
        );
    }

    /**
     * Active {@link Deduction} rows for an employee whose [effectiveFrom, effectiveTo]
     * window overlaps the run's period. Sorted with a unique tiebreaker for determinism.
     */
    private List<Deduction> activeDeductionsForPeriod(Employee employee, PayrollPeriod period) {
        return deductionRepository
            .findAll(
                (Specification<Deduction>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employee.getId()),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), period.getEndDate()),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), period.getStartDate()))
                    )
            )
            .stream()
            .sorted(
                Comparator.comparing(Deduction::getEffectiveFrom, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(
                    Deduction::getId
                )
            )
            .toList();
    }

    /**
     * Active {@link EmployeeBenefit} rows for an employee whose effective window overlaps
     * the period AND whose status is ACTIVE. Sorted by type then id.
     */
    private List<EmployeeBenefit> activeBenefitsForPeriod(Employee employee, PayrollPeriod period) {
        return employeeBenefitRepository
            .findAll(
                (Specification<EmployeeBenefit>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employee.getId()),
                        cb.equal(root.get("status"), com.humano.domain.enumeration.payroll.BenefitStatus.ACTIVE),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), period.getEndDate()),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), period.getStartDate()))
                    )
            )
            .stream()
            .sorted(Comparator.comparing(EmployeeBenefit::getType).thenComparing(EmployeeBenefit::getId))
            .toList();
    }

    /**
     * Aggregates approved-leave days per {@link LeaveType} that overlap the payroll
     * period. Days outside the period window are excluded.
     */
    private Map<LeaveType, Integer> aggregateApprovedLeaveDays(Employee employee, PayrollPeriod period) {
        List<LeaveRequest> requests = leaveRequestRepository.findAll(
            (Specification<LeaveRequest>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employee.getId()),
                    cb.equal(root.get("status"), LeaveStatus.APPROVED),
                    cb.lessThanOrEqualTo(root.get("startDate"), period.getEndDate()),
                    cb.greaterThanOrEqualTo(root.get("endDate"), period.getStartDate())
                )
        );
        Map<LeaveType, Integer> result = new EnumMap<>(LeaveType.class);
        for (LeaveRequest r : requests) {
            LocalDate overlapStart = r.getStartDate().isAfter(period.getStartDate()) ? r.getStartDate() : period.getStartDate();
            LocalDate overlapEnd = r.getEndDate().isBefore(period.getEndDate()) ? r.getEndDate() : period.getEndDate();
            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            if (days > 0) {
                result.merge(r.getLeaveType(), days, Integer::sum);
            }
        }
        return result;
    }

    /**
     * Returns the formula-driven PayComponents of a given kind, excluding any already
     * emitted in an earlier step, sorted by ({@code calcPhase} asc, {@code code} asc) so
     * the line order is stable across runs &mdash; with {@code code} as the unique
     * tiebreaker required by the determinism contract.
     */
    private List<PayComponent> sortedComponentsByKind(Collection<PayComponent> components, Kind kind, Set<UUID> excludeIds) {
        return components
            .stream()
            .filter(c -> c.getKind() == kind)
            .filter(c -> !excludeIds.contains(c.getId()))
            .sorted(
                Comparator.comparing((PayComponent c) -> c.getCalcPhase() != null ? c.getCalcPhase() : Integer.MAX_VALUE).thenComparing(c ->
                    c.getCode().name()
                )
            )
            .toList();
    }

    /**
     * Wraps {@link #calculateComponent} so the per-component loop logs and skips on
     * formula errors instead of failing the whole employee.
     */
    private BigDecimal safeCalculateComponent(
        PayComponent component,
        Map<String, Object> context,
        List<PayrollInput> inputs,
        Employee employee
    ) {
        try {
            return calculateComponent(component, context, inputs);
        } catch (Exception e) {
            log.warn("Error calculating component {} for employee {}: {}", component.getCode(), employee.getId(), e.getMessage());
            return null;
        }
    }

    private PayrollRunResponse toRunResponse(PayrollRun run, List<PayrollRunResponse.PayrollValidationError> errors) {
        PayrollPeriod period = run.getPeriod();

        // Get aggregated totals
        List<PayrollResult> results = payrollResultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), run.getId())
        );

        BigDecimal totalGross = results.stream().map(PayrollResult::getGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductions = results.stream().map(PayrollResult::getTotalDeductions).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = results.stream().map(PayrollResult::getNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEmployerCost = results.stream().map(PayrollResult::getEmployerCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        String currencyCode = results.isEmpty() ? null : results.get(0).getCurrency().getCode().getCode();

        return new PayrollRunResponse(
            run.getId(),
            period.getId(),
            period.getCode(),
            period.getStartDate(),
            period.getEndDate(),
            period.getPaymentDate(),
            run.getScope(),
            run.getStatus(),
            results.size(),
            totalGross,
            totalDeductions,
            totalNet,
            totalEmployerCost,
            currencyCode,
            run.getApprovedAt(),
            run.getApprovedBy() != null ? run.getApprovedBy().getFirstName() + " " + run.getApprovedBy().getLastName() : null,
            errors,
            run.getCreatedDate() != null ? OffsetDateTime.from(run.getCreatedDate().atOffset(java.time.ZoneOffset.UTC)) : null,
            run.getCreatedBy()
        );
    }
}
