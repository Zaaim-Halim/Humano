package com.humano.service.payroll;

import com.humano.aop.audit.Auditable;
import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.enumeration.payroll.*;
import com.humano.domain.hr.LeaveRequest;
import com.humano.domain.payroll.*;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ApprovePayrollRunRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.request.RecalculatePayrollRequest;
import com.humano.dto.payroll.response.PayrollCurrencyTotals;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.dto.payroll.response.PayrollRunSummaryResponse;
import com.humano.repository.hr.LeaveRequestRepository;
import com.humano.repository.payroll.*;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.projection.RunTotalsRow;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.security.AuthorityPermissionService;
import com.humano.security.PermissionsConstants;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.EmployeeRoleHistory;
import com.humano.service.payroll.template.PayrollTemplateService;
import com.humano.service.payroll.template.engine.PayrollTemplateEngine;
import com.humano.service.payroll.template.engine.TemplateCalculationException;
import com.humano.service.payroll.template.engine.TemplateInput;
import com.humano.service.payroll.template.engine.TemplateLine;
import com.humano.service.payroll.template.engine.TemplateProfileIncompleteException;
import com.humano.service.payroll.template.model.LineSide;
import com.humano.service.payroll.template.model.PayrollTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 *   ├─ Step  7 ─ Income tax
 *   │            progressive TaxBracket(country, PIT, period.endDate)
 *   │             × taxableIncome  →  line tagged TAX_PIT (gross deduction)
 *   │
 *   ├─ Step  8 ─ Other withholdings
 *   │            active TaxWithholding(employee, type != INCOME_TAX, period.endDate)
 *   │             × rate% × gross  →  line per row tagged TAX_PIT
 *   │            YTD update happens at POST time only (§P3.3 rule).
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
 * <p><strong>Out-of-scope hooks (lanes guarded).</strong> Multi-currency conversion at
 * the run boundary is a future task. Idempotency-hash content should not be extended
 * without careful consideration. Don't widen here.
 */
@Service
@Transactional("tenantTransactionManager")
public class PayrollProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PayrollProcessingService.class);

    /** Money scale &mdash; invariant I6: every emitted amount is rounded to this scale. */
    private static final int MONEY_SCALE = 2;

    /** Rounding mode for {@link #MONEY_SCALE}. */
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    /** Rate scale for ratio intermediates (e.g. percentage / 100) before re-scaling money. */
    private static final int RATE_SCALE = 6;

    private final PayrollRunRepository payrollRunRepository;
    private final EmployeeRoleHistory roleHistory;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollResultRepository payrollResultRepository;
    private final PayrollLineRepository payrollLineRepository;
    private final PayrollInputRepository payrollInputRepository;
    private final PayComponentRepository payComponentRepository;
    private final PayRuleRepository payRuleRepository;
    private final CompensationRepository compensationRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;
    private final BonusRepository bonusRepository;
    private final DeductionRepository deductionRepository;
    private final EmployeeBenefitRepository employeeBenefitRepository;
    private final LeaveTypeRuleRepository leaveTypeRuleRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final TaxWithholdingRepository taxWithholdingRepository;
    private final PayrollFormulaEngine formulaEngine;
    private final DeductionService deductionService;
    private final BonusService bonusService;
    private final TaxCalculationService taxCalculationService;
    private final ExchangeRateService exchangeRateService;
    private final AuthorityPermissionService authorityPermissionService;
    private final PayrollTemplateService payrollTemplateService;
    private final PayrollTemplateEngine payrollTemplateEngine;

    /** Runs each employee's calculation in its own tenant {@code REQUIRES_NEW} transaction. */
    private final PayrollEmployeeTransactionExecutor employeeTransactionExecutor;

    /** The employees a run's latest calculation could not pay; they block approval. */
    private final PayrollRunIssueRepository payrollRunIssueRepository;
    private final PayslipRepository payslipRepository;

    /** P3.4: maximum days a fallback rate may lag the payment date before the per-employee
     *  calculation fails with a {@code BusinessRuleViolationException}. */
    @org.springframework.beans.factory.annotation.Value("${humano.payroll.max-exchange-rate-staleness-days:7}")
    private int maxExchangeRateStalenessDays;

    public PayrollProcessingService(
        PayrollRunRepository payrollRunRepository,
        PayrollPeriodRepository payrollPeriodRepository,
        PayrollResultRepository payrollResultRepository,
        PayrollLineRepository payrollLineRepository,
        PayrollInputRepository payrollInputRepository,
        PayComponentRepository payComponentRepository,
        PayRuleRepository payRuleRepository,
        CompensationRepository compensationRepository,
        OrganizationSettingsRepository organizationSettingsRepository,
        EmployeeRepository employeeRepository,
        CurrencyRepository currencyRepository,
        BonusRepository bonusRepository,
        DeductionRepository deductionRepository,
        EmployeeBenefitRepository employeeBenefitRepository,
        LeaveTypeRuleRepository leaveTypeRuleRepository,
        LeaveRequestRepository leaveRequestRepository,
        TaxBracketRepository taxBracketRepository,
        TaxWithholdingRepository taxWithholdingRepository,
        PayrollFormulaEngine formulaEngine,
        DeductionService deductionService,
        BonusService bonusService,
        TaxCalculationService taxCalculationService,
        ExchangeRateService exchangeRateService,
        AuthorityPermissionService authorityPermissionService,
        PayrollEmployeeTransactionExecutor employeeTransactionExecutor,
        EmployeeRoleHistory roleHistory,
        PayrollTemplateService payrollTemplateService,
        PayrollTemplateEngine payrollTemplateEngine,
        PayrollRunIssueRepository payrollRunIssueRepository,
        PayslipRepository payslipRepository
    ) {
        this.payrollRunIssueRepository = payrollRunIssueRepository;
        this.payslipRepository = payslipRepository;
        this.roleHistory = roleHistory;
        this.payrollTemplateService = payrollTemplateService;
        this.payrollTemplateEngine = payrollTemplateEngine;
        this.payrollRunRepository = payrollRunRepository;
        this.payrollPeriodRepository = payrollPeriodRepository;
        this.payrollResultRepository = payrollResultRepository;
        this.payrollLineRepository = payrollLineRepository;
        this.payrollInputRepository = payrollInputRepository;
        this.payComponentRepository = payComponentRepository;
        this.payRuleRepository = payRuleRepository;
        this.compensationRepository = compensationRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.employeeRepository = employeeRepository;
        this.currencyRepository = currencyRepository;
        this.bonusRepository = bonusRepository;
        this.deductionRepository = deductionRepository;
        this.employeeBenefitRepository = employeeBenefitRepository;
        this.leaveTypeRuleRepository = leaveTypeRuleRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.taxWithholdingRepository = taxWithholdingRepository;
        this.formulaEngine = formulaEngine;
        this.deductionService = deductionService;
        this.bonusService = bonusService;
        this.taxCalculationService = taxCalculationService;
        this.exchangeRateService = exchangeRateService;
        this.authorityPermissionService = authorityPermissionService;
        this.employeeTransactionExecutor = employeeTransactionExecutor;
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
        // Template versions, adoption settings and employee template profiles change the calculation too.
        String templateFingerprint = payrollTemplateService.fingerprint();
        String hash = generateIdempotencyHash(
            period.getId(),
            scope,
            sortedEmployeeIds,
            payRuleVersion,
            taxBracketVersion,
            templateFingerprint,
            request.reportingCurrencyId()
        );

        // Hash short-circuit  — return any existing run with this hash to honour
        // the "two no-ops" idempotency and prevent the unique-constraint violation at
        // save time.
        Optional<PayrollRun> existingByHash = payrollRunRepository
            .findAll((Specification<PayrollRun>) (root, query, cb) -> cb.equal(root.get("hash"), hash))
            .stream()
            .findFirst();
        if (existingByHash.isPresent()) {
            PayrollRun existing = existingByHash.orElseThrow();
            log.info(
                "Idempotent re-call: short-circuiting to existing run {} (status={}, hash={})",
                existing.getId(),
                existing.getStatus(),
                hash
            );
            return toRunResponse(existing);
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
        // P3.4: attach reporting currency if requested. Null = single-currency run; no conversion at calc time.
        if (request.reportingCurrencyId() != null) {
            Currency reporting = currencyRepository
                .findById(request.reportingCurrencyId())
                .orElseThrow(() -> new EntityNotFoundException("Currency (reporting)", request.reportingCurrencyId()));
            run.setReportingCurrency(reporting);
        }

        // saveAndFlush so a unique-hash violation surfaces here (synchronously) rather than
        // at commit. The hash short-circuit above is a check-then-act with a TOCTOU window: a
        // concurrent initiate with identical inputs can insert the same hash between our check
        // and this insert. The unique constraint on payroll_run.hash blocks the duplicate;
        // translate the raw violation into a clear, retryable business error instead of a 500.
        // (A full insert-or-return would re-resolve to the winning run, but that needs a fresh
        // transaction since the failed flush marks this one rollback-only — deferred.)
        try {
            run = payrollRunRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException e) {
            log.info("Idempotency race on hash {} while creating run for period {}", hash, request.periodId());
            throw new BusinessRuleViolationException(
                "A payroll run for these exact inputs was just created concurrently. Retry the request to retrieve it."
            );
        }
        log.info(
            "Created payroll run {} with status DRAFT, hash={}, reportingCurrency={}",
            run.getId(),
            hash,
            run.getReportingCurrency() == null ? "<native-only>" : run.getReportingCurrency().getCode().name()
        );

        return toRunResponse(run);
    }

    /**
     * Calculates payroll for all employees in a run.
     *
     * <h3>Transaction model</h3>
     * The orchestration here runs in one tenant transaction, but each employee is calculated
     * in its <em>own</em> {@code REQUIRES_NEW} transaction via
     * {@link PayrollEmployeeTransactionExecutor#calculateInNewTransaction}. That buys:
     * <ul>
     *   <li><strong>Genuine per-employee isolation.</strong> Previously a caught per-employee
     *       failure was <em>not</em> truly isolated: rows already saved by the failing employee
     *       (e.g. the {@link PayrollResult} persisted just before its lines threw) sat in the
     *       shared persistence context and flushed at the final commit alongside everyone else
     *       — a half-written result could be persisted. Now each employee fully commits or
     *       fully rolls back, and a failure is recorded as a
     *       {@link PayrollRunResponse.PayrollValidationError} without touching anyone else.</li>
     *   <li><strong>Bounded memory.</strong> Each employee's heavy entity graph lives in its
     *       own transaction-scoped persistence context (closed at that employee's commit), so
     *       it never accumulates across thousands of employees in one giant context.</li>
     * </ul>
     *
     * <p><strong>Concurrency caveat.</strong> Per-employee commits remove the implicit
     * whole-run serialization. {@link #resolveResult} does an unlocked find-or-create on
     * {@code (run, employee)}; the durable guard against a concurrent double-calculate is the
     * unique constraint {@code uc_payroll_result_run_employee} on
     * {@code payroll_result(run_id, employee_id)} — a racing insert fails and surfaces as a
     * per-employee error instead of a duplicate row.
     */
    @Transactional(transactionManager = "tenantTransactionManager")
    public PayrollRunResponse calculatePayroll(UUID runId) {
        log.info("Calculating payroll for run: {}", runId);

        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        if (run.getStatus() != RunStatus.DRAFT && run.getStatus() != RunStatus.CALCULATED) {
            throw new BusinessRuleViolationException("Can only calculate payroll for DRAFT or CALCULATED runs");
        }

        List<Employee> employees = getEmployeesForScope(run.getScope(), run.getPeriod());

        // Pay components are tenant-global config — load the lookup map ONCE for the whole
        // run instead of re-querying pay_component for every employee. The components are only
        // read and used as non-cascading FK targets on emitted lines, so passing them into each
        // per-employee transaction (where they are technically detached) is safe.
        Map<PayComponentCode, PayComponent> componentsByCode = loadComponentsByCode();

        List<PayrollRunResponse.PayrollValidationError> errors = new ArrayList<>();
        Set<UUID> calculated = new HashSet<>();
        int processedCount = 0;
        for (Employee employee : employees) {
            UUID employeeId = employee.getId();
            try {
                // REQUIRES_NEW: the executor suspends this orchestration transaction and runs
                // the work in its own, so a failure rolls back only that employee. Run + employee
                // are re-loaded inside that transaction (the instances above are managed by the
                // orchestration context, hence detached for the new one).
                employeeTransactionExecutor.runInNewTransaction(() -> {
                    PayrollRun freshRun = payrollRunRepository
                        .findById(runId)
                        .orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));
                    Employee freshEmployee = employeeRepository
                        .findById(employeeId)
                        .orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));
                    calculateEmployeePayroll(freshRun, freshRun.getPeriod(), freshEmployee, componentsByCode);
                });
                calculated.add(employeeId);
                processedCount++;
            } catch (Exception e) {
                String name = employee.getFirstName() + " " + employee.getLastName();
                TemplateProfileIncompleteException incomplete = causeOfType(e, TemplateProfileIncompleteException.class);
                if (incomplete != null) {
                    // Expected until an administrator fills in the details: no stack trace, and the
                    // template is named so the screen can link to them.
                    log.warn("Payroll for employee {} skipped: {} ({})", employeeId, incomplete.getMessage(), incomplete.getTemplateId());
                    errors.add(
                        new PayrollRunResponse.PayrollValidationError(
                            employeeId,
                            name,
                            "TEMPLATE_PROFILE_INCOMPLETE",
                            incomplete.getMessage(),
                            "ERROR",
                            incomplete.getTemplateId()
                        )
                    );
                    continue;
                }
                // Log the full throwable (stack trace), not just getMessage(), so a
                // production calc failure is actually diagnosable. The catch stays broad on
                // purpose — per-employee isolation means one employee's failure must not abort
                // the run — but the diagnostics are no longer discarded.
                log.error("Error calculating payroll for employee {}", employeeId, e);
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                errors.add(new PayrollRunResponse.PayrollValidationError(employeeId, name, "CALCULATION_ERROR", message, "ERROR", null));
            }
        }

        // Someone this calculation could not pay must not keep a result from an earlier one (they
        // would be paid stale amounts); they stay listed with the run, blocking approval, until the
        // next calculation.
        removeResultsExcept(run, calculated);
        replaceIssues(run, errors);
        run.setIssueOverrideReason(null);
        run.setStatus(RunStatus.CALCULATED);
        run = payrollRunRepository.save(run);

        log.info("Completed payroll calculation for run {}. Processed: {}, Errors: {}", runId, processedCount, errors.size());

        return toRunResponse(run);
    }

    /**
     * Deletes the run's results (with their lines, and payslips of a recalculated approved run) for
     * everyone not in {@code calculated}: employees the calculation refused, or no longer in scope.
     */
    private void removeResultsExcept(PayrollRun run, Set<UUID> calculated) {
        List<PayrollResult> stale = payrollResultRepository
            .findAll((Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), run.getId()))
            .stream()
            .filter(r -> !calculated.contains(r.getEmployee().getId()))
            .toList();
        for (PayrollResult result : stale) {
            UUID resultId = result.getId();
            payslipRepository.deleteAll(
                payslipRepository.findAll((Specification<Payslip>) (root, query, cb) -> cb.equal(root.get("result").get("id"), resultId))
            );
            payrollLineRepository.deleteAll(
                payrollLineRepository.findAll(
                    (Specification<PayrollLine>) (root, query, cb) -> cb.equal(root.get("result").get("id"), resultId)
                )
            );
            payrollResultRepository.delete(result);
        }
        if (!stale.isEmpty()) {
            log.info("Removed {} result(s) of run {} for employees this calculation did not pay", stale.size(), run.getId());
        }
    }

    /** Replaces the run's stored issues with this calculation's. */
    private void replaceIssues(PayrollRun run, List<PayrollRunResponse.PayrollValidationError> errors) {
        payrollRunIssueRepository.deleteByRunId(run.getId());
        payrollRunIssueRepository.saveAll(
            errors
                .stream()
                .map(e -> {
                    PayrollRunIssue issue = new PayrollRunIssue();
                    issue.setRun(run);
                    issue.setEmployee(employeeRepository.getReferenceById(e.employeeId()));
                    issue.setErrorCode(e.errorCode());
                    issue.setMessage(e.message());
                    issue.setTemplateId(e.templateId());
                    return issue;
                })
                .toList()
        );
    }

    /** The employees the run's latest calculation could not pay, as on the calculation response. */
    private List<PayrollRunResponse.PayrollValidationError> issuesOf(PayrollRun run) {
        return payrollRunIssueRepository
            .findByRunId(run.getId())
            .stream()
            .map(i ->
                new PayrollRunResponse.PayrollValidationError(
                    i.getEmployee().getId(),
                    i.getEmployee().getFirstName() + " " + i.getEmployee().getLastName(),
                    i.getErrorCode(),
                    i.getMessage(),
                    "ERROR",
                    i.getTemplateId()
                )
            )
            .toList();
    }

    /**
     * Calculates payroll for a single employee following the canonical §2.2 pipeline .
     * See the class-level sequence diagram for the contract.
     */
    private PayrollResult calculateEmployeePayroll(
        PayrollRun run,
        PayrollPeriod period,
        Employee employee,
        Map<PayComponentCode, PayComponent> componentsByCode
    ) {
        log.debug("Calculating payroll for employee: {}", employee.getId());

        // ---- Resolve compensation (required) ----
        // Resolved BEFORE resolveResult so a missing-comp failure doesn't leave behind a
        // recalc victim with its lines already deleted (resolveResult deletes existing
        // lines on the recalc path).
        Compensation compensation = findActiveCompensation(employee.getId(), period.getEndDate());
        if (compensation == null) {
            throw new BusinessRuleViolationException("No active compensation found for employee " + employee.getId());
        }

        // ---- P3.4: pre-validate reporting rate (fail-fast for recalc safety) ----
        // If the run has a reporting currency, fetch the rate NOW — before any line
        // deletion in resolveResult. A stale/missing rate throws here and propagates to
        // calculatePayroll's per-employee try/catch, which surfaces it as a
        // PayrollValidationError. The result row + lines are untouched: clean failure.
        ExchangeRateService.ReportingRate preResolvedRate = null;
        if (run.getReportingCurrency() != null) {
            preResolvedRate = exchangeRateService.getReportingRate(
                compensation.getCurrency().getId(),
                run.getReportingCurrency().getId(),
                period.getPaymentDate(),
                maxExchangeRateStalenessDays
            );
        }

        // ---- Resolve or create result, clear previous lines ----
        PayrollResult result = resolveResult(run, period, employee);
        result.setCurrency(compensation.getCurrency());

        // ---- Pipeline state ----
        List<PayrollLine> lines = new ArrayList<>();
        SequenceCounter seq = new SequenceCounter();
        OrganizationSettings settings = resolveSettings();
        BigDecimal baseSalary = calculateBaseSalary(compensation, period, settings).setScale(MONEY_SCALE, MONEY_ROUNDING);
        List<PayrollInput> inputs = getPayrollInputs(employee.getId(), period.getId());
        // Loaded once and shared with step 8 (other-withholdings) below: buildCalculationContext
        // exposes each row's running YTD so capped formulas can reference it; step 8 emits the lines.
        List<TaxWithholding> activeWithholdings = activeWithholdingsForPeriod(employee, period.getEndDate());
        Map<String, Object> context = buildCalculationContext(
            employee,
            compensation,
            period,
            inputs,
            baseSalary,
            settings,
            activeWithholdings
        );

        BigDecimal gross = BigDecimal.ZERO;
        // Gross earnings by pay-component code, for payroll templates whose law taxes some kinds of pay differently.
        Map<String, BigDecimal> earningsByComponent = new HashMap<>();
        BigDecimal nonTaxableEarnings = BigDecimal.ZERO;
        // Earnings whose component is not subject to social contributions; payroll templates use
        // gross minus these as their contribution base.
        BigDecimal nonSocialEarnings = BigDecimal.ZERO;
        BigDecimal preTaxDeductions = BigDecimal.ZERO;
        BigDecimal postTaxDeductions = BigDecimal.ZERO;
        BigDecimal taxWithheld = BigDecimal.ZERO;
        BigDecimal employerCharges = BigDecimal.ZERO;
        BigDecimal benefitEmployerCost = BigDecimal.ZERO;

        // componentsByCode is the tenant-global pay-component lookup, loaded once per run
        // and passed in. Used here read-only plus as non-cascading FK targets on emitted lines.
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
            lines.add(
                emitLine(result, basicComponent, null, null, baseSalary, seq.next(), explain).lineCategory(PayrollLineCategory.BASE_SALARY)
            );
            handledComponentIds.add(basicComponent.getId());
            if (!Boolean.TRUE.equals(basicComponent.getTaxable())) {
                nonTaxableEarnings = nonTaxableEarnings.add(baseSalary);
            }
            if (!Boolean.TRUE.equals(basicComponent.getContributesToSocial())) {
                nonSocialEarnings = nonSocialEarnings.add(baseSalary);
            }
            context.put(basicComponent.getCode().name(), baseSalary);
        } else {
            log.warn("Step 1 — no BASIC PayComponent seeded; base salary {} counted in gross without a line", fmt(baseSalary));
        }
        gross = gross.add(baseSalary);
        earningsByComponent.merge(PayComponentCode.BASIC.name(), baseSalary, BigDecimal::add);

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
            lines.add(
                emitLine(result, input.getComponent(), input.getQuantity(), input.getRate(), amt, seq.next(), explain).lineCategory(
                    PayrollLineCategory.EARNING
                )
            );
            gross = gross.add(amt);
            earningsByComponent.merge(input.getComponent().getCode().name(), amt, BigDecimal::add);
            if (!Boolean.TRUE.equals(input.getComponent().getTaxable())) {
                nonTaxableEarnings = nonTaxableEarnings.add(amt);
            }
            if (!Boolean.TRUE.equals(input.getComponent().getContributesToSocial())) {
                nonSocialEarnings = nonSocialEarnings.add(amt);
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
                lines.add(emitLine(result, bonusComponent, null, null, amt, seq.next(), explain).lineCategory(PayrollLineCategory.EARNING));
                gross = gross.add(amt);
                earningsByComponent.merge(PayComponentCode.BONUS.name(), amt, BigDecimal::add);
                if (!Boolean.TRUE.equals(bonusComponent.getTaxable())) {
                    nonTaxableEarnings = nonTaxableEarnings.add(amt);
                }
                if (!Boolean.TRUE.equals(bonusComponent.getContributesToSocial())) {
                    nonSocialEarnings = nonSocialEarnings.add(amt);
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
            lines.add(emitLine(result, component, null, null, amt, seq.next(), explain).lineCategory(PayrollLineCategory.EARNING));
            gross = gross.add(amt);
            earningsByComponent.merge(component.getCode().name(), amt, BigDecimal::add);
            if (!Boolean.TRUE.equals(component.getTaxable())) {
                nonTaxableEarnings = nonTaxableEarnings.add(amt);
            }
            if (!Boolean.TRUE.equals(component.getContributesToSocial())) {
                nonSocialEarnings = nonSocialEarnings.add(amt);
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
        boolean hadLeaveDeduction = false;
        // Pre-tax leave deductions: remuneration not earned, which payroll templates take off their bases.
        BigDecimal leaveReductions = BigDecimal.ZERO;
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
                            ),
                        // Deterministic single-row pick (LIMIT 1, id tiebreak) pushed into SQL.
                        PageRequest.of(0, 1, Sort.by(Sort.Order.asc("id")))
                    )
                    .stream()
                    .findFirst();
                if (
                    ruleOpt.isEmpty() ||
                    ruleOpt.orElseThrow().getDeductionPercentage() == null ||
                    ruleOpt.orElseThrow().getDeductionPercentage().signum() == 0
                ) {
                    continue;
                }
                LeaveTypeRule rule = ruleOpt.orElseThrow();
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
                lines.add(
                    emitLine(
                        result,
                        leaveDeductionComponent,
                        BigDecimal.valueOf(days),
                        dailyRate,
                        deduction,
                        seq.next(),
                        explain
                    ).lineCategory(PayrollLineCategory.LEAVE_DEDUCTION)
                );
                if (affectsTaxable) {
                    preTaxDeductions = preTaxDeductions.add(deduction);
                    leaveReductions = leaveReductions.add(deduction);
                } else {
                    postTaxDeductions = postTaxDeductions.add(deduction);
                }
                hadLeaveDeduction = true;
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
        Map<String, BigDecimal> preTaxDeductionsByType = new HashMap<>();
        for (Deduction d : activeDeductions.stream().filter(d -> Boolean.TRUE.equals(d.getIsPreTax())).toList()) {
            BigDecimal amt = computeDeductionAmount(d, grossPay);
            if (amt.signum() == 0) continue;
            if (generalDeductionComponent == null) {
                log.warn("Step 5 — Pre-tax deduction {} skipped: no DEDUCTION PayComponent seeded", d.getId());
                continue;
            }
            String explain = explainDeduction(d, amt, grossPay, "Step 5", true);
            lines.add(
                emitLine(result, generalDeductionComponent, null, null, amt, seq.next(), explain).lineCategory(
                    PayrollLineCategory.PRE_TAX_DEDUCTION
                )
            );
            preTaxDeductions = preTaxDeductions.add(amt);
            preTaxDeductionsByType.merge(d.getType() != null ? d.getType().name() : "OTHER", amt, BigDecimal::add);
        }

        // ============================================================
        // Step 6 — Taxable income marker
        //   TenantInitializationService seeds BASIC with taxable=true, so base salary is
        //   counted into the taxable base. Components whose taxable=false (today: BONUS)
        //   accumulate into nonTaxableEarnings and reduce the taxable base accordingly.
        // ============================================================
        BigDecimal taxableIncome = grossPay.subtract(preTaxDeductions).subtract(nonTaxableEarnings).max(BigDecimal.ZERO);
        context.put("taxableIncome", taxableIncome);

        // ============================================================
        // Step 6b — Statutory payroll templates
        //   Enabled templates covering the employee's country compute income tax and social
        //   contributions from shipped, cited template data. The tax types they produce are then
        //   theirs: step 7 skips bracket codes and step 8 skips withholding rows of those types, and
        //   formula rules on TAX_PIT / EMPLOYER_CHARGE don't run for this employee. Lines are tagged
        //   with the template id@version and step (audit trail; year-to-date sums read them back).
        // ============================================================
        Set<TaxType> templateLedgers = EnumSet.noneOf(TaxType.class);
        List<PayrollTemplateService.AppliedTemplate> templates = payrollTemplateService.applicableTo(employee);
        if (!templates.isEmpty()) {
            PayComponent templateTaxComponent = componentsByCode.get(PayComponentCode.TAX_PIT);
            PayComponent templateEmployerComponent = componentsByCode.get(PayComponentCode.EMPLOYER_CHARGE);
            if (templateTaxComponent == null || templateEmployerComponent == null) {
                throw new TemplateCalculationException(
                    "Payroll templates need the TAX_PIT and EMPLOYER_CHARGE pay components; save the template adoption again to create them"
                );
            }
            handledComponentIds.add(templateTaxComponent.getId());
            handledComponentIds.add(templateEmployerComponent.getId());
            boolean fullPeriod =
                employedThroughout(employee, period) &&
                !hadLeaveDeduction &&
                (employee.getFte() == null || employee.getFte().compareTo(BigDecimal.ONE) >= 0);
            BigDecimal otherPreTaxDeductions = preTaxDeductions.subtract(leaveReductions);
            for (PayrollTemplateService.AppliedTemplate applied : templates) {
                PayrollTemplate template = applied.template();
                TemplateInput input = new TemplateInput(
                    period.getStartDate(),
                    period.getEndDate(),
                    period.getPaymentDate(),
                    period.getCalendar().getFrequency(),
                    compensation.getCurrency().getCode().getCode(),
                    grossPay,
                    earningsByComponent,
                    grossPay.subtract(nonSocialEarnings),
                    grossPay.subtract(nonTaxableEarnings),
                    leaveReductions,
                    otherPreTaxDeductions,
                    preTaxDeductionsByType,
                    fullPeriod,
                    employee.getFte(),
                    employee.getBirthDate(),
                    employee.getStartDate(),
                    employedThroughout(employee, period),
                    applied.settings(),
                    applied.attributes(),
                    payrollTemplateService.yearToDate(employee.getId(), template, period.getStartDate(), period.getPaymentDate())
                );
                for (TemplateLine templateLine : payrollTemplateEngine.calculate(template, input)) {
                    boolean employerSide = templateLine.side() == LineSide.EMPLOYER;
                    PayrollLineCategory category =
                        switch (templateLine.side()) {
                            case EMPLOYEE -> PayrollLineCategory.WITHHOLDING;
                            case EMPLOYER -> PayrollLineCategory.EMPLOYER_CHARGE;
                            case TAX -> PayrollLineCategory.INCOME_TAX;
                        };
                    // Line rates are stored as percentages, like withholding lines.
                    BigDecimal ratePercent = templateLine.rate() == null ? null : templateLine.rate().movePointRight(2);
                    PayrollLine line = emitLine(
                        result,
                        employerSide ? templateEmployerComponent : templateTaxComponent,
                        templateLine.base(),
                        ratePercent,
                        templateLine.amount(),
                        seq.next(),
                        "Step 6b — " + templateLine.explain()
                    )
                        .lineCategory(category)
                        .taxType(templateLine.ledger())
                        .label(templateLine.label());
                    line.setTemplateRef(templateLine.templateRef());
                    line.setTemplateStep(templateLine.step());
                    lines.add(line);
                    if (employerSide) {
                        employerCharges = employerCharges.add(templateLine.amount());
                    } else {
                        taxWithheld = taxWithheld.add(templateLine.amount());
                    }
                }
                templateLedgers.addAll(applied.ledgers());
            }
        }

        // ============================================================
        // Step 7 — Income tax (progressive, country-aware)
        //   Looks up the active TaxBracket rows for (employee.country, PIT, period.endDate)
        //   and applies the spec'd progressive algorithm, annualising via the calendar's
        //   frequency when the set's basis is ANNUAL. The emitted line is tagged with
        //   lineCategory=INCOME_TAX and taxType=INCOME_TAX; those typed fields are the
        //   contract the post-time YTD reader (updateYearToDateOnPost) keys off.
        // ============================================================
        PayComponent taxPitComponent = componentsByCode.get(PayComponentCode.TAX_PIT);
        if (employee.getCountry() == null) {
            log.debug("Step 7 — income tax skipped: employee {} has no country", employee.getId());
        } else if (taxPitComponent == null) {
            log.warn("Step 7 — income tax skipped: no TAX_PIT PayComponent seeded");
        } else {
            // Evaluate every configured income-tax code via the bracket path (national PIT plus any
            // state/local brackets the country seeded), not just PIT. A PIT-only country — the only
            // shape with data today — yields exactly the single PIT line as before. Each code's brackets
            // are applied to the same taxableIncome; per-code taxable-base adjustments (e.g. US state
            // income computed on a different base than federal) are a separate follow-up coupled to #5.
            // ANNUAL bracket sets are annualised with the calendar's frequency; PER_PERIOD sets apply as-is.
            // A malformed set (gap, overlap, no 0 start, mixed basis) throws and surfaces as a validation
            // error for this employee rather than producing a mis-sliced tax.
            int periodsPerYear = period.getCalendar().getFrequency().periodsPerYear();
            List<IncomeTaxComponent> incomeTaxes = computeIncomeTaxes(
                taxableIncome,
                code ->
                    templateLedgers.contains(INCOME_TAX_LEDGER_TYPE.get(code))
                        ? List.of()
                        : taxCalculationService.getActiveBracketsForCalculation(employee.getCountry().getId(), code, period.getEndDate()),
                (income, brackets) -> taxCalculationService.calculatePeriodTax(income, brackets, periodsPerYear)
            );
            if (incomeTaxes.isEmpty()) {
                log.debug(
                    "Step 7 — no active income-tax brackets for country {} on {}; taxableIncome={}",
                    employee.getCountry().getCode(),
                    period.getEndDate(),
                    fmt(taxableIncome)
                );
            } else {
                for (IncomeTaxComponent it : incomeTaxes) {
                    String explain =
                        "Step 7 — Income tax (" +
                        it.code() +
                        ", country=" +
                        employee.getCountry().getCode() +
                        ", taxableIncome=" +
                        fmt(taxableIncome) +
                        ", brackets=" +
                        it.bracketCount() +
                        "): " +
                        fmt(it.amount());
                    lines.add(
                        emitLine(result, taxPitComponent, null, null, it.amount(), seq.next(), explain)
                            .lineCategory(PayrollLineCategory.INCOME_TAX)
                            .taxType(it.ledgerType())
                    );
                    taxWithheld = taxWithheld.add(it.amount());
                }
                // Once a bracket-driven income-tax line lands, exclude TAX_PIT from the step 10 formula
                // loop so a tenant that ALSO seeded a TAX_PIT PayRule doesn't get tax counted twice in
                // net pay. (When no bracket line was emitted, leave TAX_PIT eligible so a formula-only
                // fallback still works.)
                handledComponentIds.add(taxPitComponent.getId());
            }
        }

        // ============================================================
        // Step 8 — Other withholdings (TaxWithholding rows; non-INCOME_TAX)
        //   INCOME_TAX is handled by step 7's bracket calc; iterating it here would
        //   double-count. Each surviving row contributes a line via rate% × gross,
        //   tagged lineCategory=WITHHOLDING and taxType=<the row's type> — the typed
        //   contract the post-time YTD reader keys off.
        // ============================================================
        // activeWithholdings loaded once near the top of this method (shared with the formula context).
        for (TaxWithholding w : activeWithholdings) {
            if (w.getType() == TaxType.INCOME_TAX) continue;
            if (templateLedgers.contains(w.getType())) continue; // computed by a payroll template (step 6b)
            BigDecimal amt = computeWithholdingAmount(w, grossPay);
            if (amt.signum() == 0) continue;
            if (taxPitComponent == null) {
                log.warn("Step 8 — withholding {} ({}) skipped: no TAX_PIT PayComponent seeded", w.getId(), w.getType());
                continue;
            }
            // Same double-count guard as step 7: once a step-8 line lands, step 10's
            // formula loop should not re-process TAX_PIT.
            handledComponentIds.add(taxPitComponent.getId());
            String explain =
                "Step 8 — Withholding (" +
                w.getType() +
                ", " +
                fmt(w.getRate()) +
                "% × gross " +
                fmt(grossPay) +
                " = " +
                fmt(amt) +
                ", authority=" +
                w.getTaxAuthority() +
                ")";
            lines.add(
                emitLine(result, taxPitComponent, null, w.getRate(), amt, seq.next(), explain)
                    .lineCategory(PayrollLineCategory.WITHHOLDING)
                    .taxType(w.getType())
            );
            taxWithheld = taxWithheld.add(amt);
        }

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
                    lines.add(
                        emitLine(result, generalDeductionComponent, null, null, employeeCost, seq.next(), explain).lineCategory(
                            PayrollLineCategory.BENEFIT_DEDUCTION
                        )
                    );
                    postTaxDeductions = postTaxDeductions.add(employeeCost);
                }
            }
            if (employerCost.signum() != 0) {
                if (employerChargeComponent != null) {
                    String explain = "Step 9 — Benefit (" + b.getType() + ") employer cost: " + fmt(employerCost);
                    lines.add(
                        emitLine(result, employerChargeComponent, null, null, employerCost, seq.next(), explain).lineCategory(
                            PayrollLineCategory.EMPLOYER_CHARGE
                        )
                    );
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
            lines.add(
                emitLine(result, generalDeductionComponent, null, null, amt, seq.next(), explain).lineCategory(
                    PayrollLineCategory.POST_TAX_DEDUCTION
                )
            );
            postTaxDeductions = postTaxDeductions.add(amt);
        }
        // Formula-driven DEDUCTION PayComponents in deterministic order
        for (PayComponent component : sortedComponentsByKind(componentsByCode.values(), Kind.DEDUCTION, handledComponentIds)) {
            BigDecimal amt = safeCalculateComponent(component, context, inputs, employee);
            if (amt == null || amt.signum() == 0) continue;
            amt = amt.setScale(MONEY_SCALE, MONEY_ROUNDING).abs();
            String explain = "Step 10 — Deduction component '" + component.getCode() + "' via PayRule formula: " + fmt(amt);
            lines.add(
                emitLine(result, component, null, null, amt, seq.next(), explain).lineCategory(PayrollLineCategory.POST_TAX_DEDUCTION)
            );
            postTaxDeductions = postTaxDeductions.add(amt);
            context.put(component.getCode().name(), amt);
        }

        // ============================================================
        // Step 11 — NET PAY marker
        //   totalDeductions includes preTax, postTax, AND step 7/8 withholdings
        //   (taxWithheld) so net = gross − everything-withheld-from-the-employee.
        // ============================================================
        BigDecimal totalDeductions = preTaxDeductions.add(postTaxDeductions).add(taxWithheld);
        BigDecimal netPay = grossPay.subtract(totalDeductions);

        // ============================================================
        // Step 12 — EMPLOYER COST marker (+ formula-driven employer charges)
        // ============================================================
        for (PayComponent component : sortedComponentsByKind(componentsByCode.values(), Kind.EMPLOYER_CHARGE, handledComponentIds)) {
            BigDecimal amt = safeCalculateComponent(component, context, inputs, employee);
            if (amt == null || amt.signum() == 0) continue;
            amt = amt.setScale(MONEY_SCALE, MONEY_ROUNDING);
            String explain = "Step 12 — Employer charge '" + component.getCode() + "' via PayRule formula: " + fmt(amt);
            lines.add(emitLine(result, component, null, null, amt, seq.next(), explain).lineCategory(PayrollLineCategory.EMPLOYER_CHARGE));
            employerCharges = employerCharges.add(amt);
            context.put(component.getCode().name(), amt);
        }
        BigDecimal employerCost = grossPay.add(employerCharges).add(benefitEmployerCost);

        // ---- Persist totals + lines ----
        result.setGross(grossPay);
        result.setTotalDeductions(totalDeductions);
        result.setNet(netPay);
        result.setEmployerCost(employerCost);
        // P3.4 — Multi-currency conversion at the run boundary.
        applyReportingConversion(run, result, period, preResolvedRate, grossPay, totalDeductions, netPay, employerCost);
        result = payrollResultRepository.save(result);

        for (PayrollLine line : lines) {
            line.setResult(result);
        }
        payrollLineRepository.saveAll(lines);

        log.debug(
            "Pipeline done for employee {}: gross={}, taxable={}, taxWithheld={}, totalDeductions={}, net={}, employerCost={}, lines={}",
            employee.getId(),
            fmt(grossPay),
            fmt(taxableIncome),
            fmt(taxWithheld),
            fmt(totalDeductions),
            fmt(netPay),
            fmt(employerCost),
            lines.size()
        );
        return result;
    }

    /**
     * Loads the tenant-global pay-component lookup keyed by {@link PayComponentCode}. Called
     * once per run rather than once per employee. {@code PayComponent.code} is
     * unique-constrained at the entity level, so the merge function never actually fires — it
     * is only there to satisfy {@link Collectors#toMap}'s signature.
     */
    private Map<PayComponentCode, PayComponent> loadComponentsByCode() {
        return payComponentRepository
            .findAll()
            .stream()
            .filter(c -> c.getCode() != null)
            .collect(Collectors.toMap(PayComponent::getCode, c -> c, (a, b) -> a));
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
            PayrollResult result = existing.orElseThrow();
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
        validateApprovalAuthority(approver, run, request.forceApproval());

        // Employees the calculation could not pay are not paid by this run: approving it anyway
        // takes an explicit override with a reason, recorded on the run.
        long issues = payrollRunIssueRepository.countByRunId(run.getId());
        String overrideReason = request.issueOverrideReason() != null ? request.issueOverrideReason().trim() : "";
        if (issues > 0 && (!request.approveDespiteIssues() || overrideReason.isEmpty())) {
            throw new BusinessRuleViolationException(
                issues +
                " employee(s) could not be calculated and would not be paid by this run. Fix them and recalculate, " +
                "or approve without them and give the reason."
            );
        }
        if (issues > 0) {
            log.warn("Run {} approved without {} employee(s) it could not calculate: {}", run.getId(), issues, overrideReason);
        }
        run.setIssueOverrideReason(issues > 0 ? overrideReason : null);
        run.setStatus(RunStatus.APPROVED);
        run.setApprovedAt(OffsetDateTime.now());
        run.setApprovedBy(approver);

        // Flush inside the transaction so a concurrent approve/recalculate that already
        // advanced this run's version surfaces as a clean business error rather than a
        // commit-time 500 (and never lets two approvals race past the CALCULATED guard).
        try {
            run = payrollRunRepository.save(run);
            payrollRunRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification while approving run {}: {}", request.payrollRunId(), e.getMessage());
            throw new BusinessRuleViolationException(
                "Payroll run " +
                request.payrollRunId() +
                " was modified concurrently (already approved or under recalculation). Reload and retry."
            );
        }
        log.info("Payroll run {} approved by {}", run.getId(), approver.getId());

        return toRunResponse(run);
    }

    /**
     * Posts an approved payroll run, making it final.
     *
     * <p>§P3.3 contract: YTD totals on {@link TaxWithholding} rows are updated <b>only here</b>
     * (POSTED transition). DRAFT/CALCULATED/APPROVED runs leave YTD untouched so a
     * recalculated or rejected run never poisons the year-to-date ledger.
     */
    @Auditable(action = "PAYROLL_POSTED", targetType = "PayrollRun", targetIdExpression = "#runId")
    public PayrollRunResponse postPayrollRun(UUID runId) {
        log.info("Posting payroll run: {}", runId);

        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        if (run.getStatus() != RunStatus.APPROVED) {
            throw new BusinessRuleViolationException("Can only post APPROVED payroll runs. Current status: " + run.getStatus());
        }

        run.setStatus(RunStatus.POSTED);

        // §P3.3: YTD update happens before the period closes. All three writes (run status,
        // YTD ledger, period close) commit together — post is all-or-nothing.
        //
        // The explicit flush() converts the commit-time optimistic-lock check into a
        // catchable failure HERE, inside the transaction. Two concurrent posts on the same
        // APPROVED run both pass the status guard above; the loser's flush bumps payroll_run
        // (or a tax_withholding YTD row) against a version the winner already advanced,
        // throws, and its whole transaction — including the YTD increments — rolls back.
        // Without this, the loser would silently double-count the tax ledger.
        PayrollPeriod period = run.getPeriod();
        period.setClosed(true);
        try {
            run = payrollRunRepository.save(run);
            updateYearToDateOnPost(run);
            payrollPeriodRepository.save(period);
            payrollRunRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("Concurrent modification while posting run {}: {}", runId, e.getMessage());
            throw new BusinessRuleViolationException(
                "Payroll run " +
                runId +
                " (or one of its tax-withholding rows) was modified concurrently while posting. " +
                "It may already be posted or under recalculation — reload and retry."
            );
        }

        log.info("Payroll run {} posted and period {} closed", run.getId(), period.getId());

        return toRunResponse(run);
    }

    /**
     * Walks the persisted lines of every {@link PayrollResult} in this run, isolates the
     * income-tax and withholding lines via their typed {@link PayrollLine#getLineCategory()}
     * / {@link PayrollLine#getTaxType()} fields, and adds the amounts to the matching
     * {@link TaxWithholding#yearToDateAmount}.
     *
     * <p>The classification is structured data set at line emission (in
     * {@link #calculateEmployeePayroll}), not parsed from the human-readable {@code explain}
     * text. This is the typed replacement for the former explain-prefix protocol, so editing
     * a log/explain string can no longer silently break the tax ledger.
     *
     * <p>Per-line resolution semantics:
     * <ul>
     *   <li><b>{@link PayrollLineCategory#INCOME_TAX}</b> → looks up the employee's active
     *       {@link TaxType#INCOME_TAX} row at {@code period.endDate} and adds {@code line.amount}
     *       to its YTD.</li>
     *   <li><b>{@link PayrollLineCategory#WITHHOLDING}</b> → routes by the line's
     *       {@link PayrollLine#getTaxType()} to that type's active row and adds {@code line.amount}
     *       to its YTD.</li>
     * </ul>
     *
     * <p>If no active row is configured for a tax type that produced a line, the amount is
     * logged but skipped — the line still appears on the payslip; the YTD ledger just
     * doesn't get a sink for it. This is the "tenant has not set up the bookkeeping side"
     * gap, not a calc bug.
     */
    private void updateYearToDateOnPost(PayrollRun run) {
        LocalDate asOfDate = run.getPeriod().getEndDate();
        final UUID runId = run.getId();
        List<PayrollResult> results = payrollResultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), runId)
        );
        // Fetch every line for the run in ONE query and group by result, instead of a
        // per-result query inside the loop (which was O(employees) round-trips).
        Map<UUID, List<PayrollLine>> linesByResult = payrollLineRepository
            .findAll((Specification<PayrollLine>) (root, query, cb) -> cb.equal(root.get("result").get("run").get("id"), runId))
            .stream()
            .collect(Collectors.groupingBy(line -> line.getResult().getId()));
        int incomeTaxUpdates = 0;
        int otherWithholdingUpdates = 0;
        for (PayrollResult result : results) {
            Employee employee = result.getEmployee();
            List<PayrollLine> lines = linesByResult.getOrDefault(result.getId(), List.of());
            BigDecimal incomeTaxAmount = BigDecimal.ZERO;
            Map<TaxType, BigDecimal> withholdingByType = new EnumMap<>(TaxType.class);
            for (PayrollLine line : lines) {
                if (line.getTemplateRef() != null) {
                    continue; // template year-to-date is read from the lines themselves
                }
                BigDecimal amt = nz(line.getAmount());
                PayrollLineCategory category = line.getLineCategory();
                if (category == PayrollLineCategory.INCOME_TAX) {
                    incomeTaxAmount = incomeTaxAmount.add(amt);
                } else if (category == PayrollLineCategory.WITHHOLDING && line.getTaxType() != null) {
                    withholdingByType.merge(line.getTaxType(), amt, BigDecimal::add);
                }
            }
            if (incomeTaxAmount.signum() > 0 && bumpYtdForType(employee.getId(), TaxType.INCOME_TAX, incomeTaxAmount, asOfDate)) {
                incomeTaxUpdates++;
            }
            for (Map.Entry<TaxType, BigDecimal> e : withholdingByType.entrySet()) {
                if (bumpYtdForType(employee.getId(), e.getKey(), e.getValue(), asOfDate)) {
                    otherWithholdingUpdates++;
                }
            }
        }
        log.info(
            "Post YTD update on run {}: incomeTaxUpdates={}, otherWithholdingUpdates={}, resultsScanned={}",
            runId,
            incomeTaxUpdates,
            otherWithholdingUpdates,
            results.size()
        );
    }

    /**
     * Adds {@code amount} to the active {@link TaxWithholding} row of ({@code employeeId},
     * {@code type}) on {@code asOfDate}. Returns {@code true} when a row was found and
     * updated, {@code false} when no row exists (logged at DEBUG since this is a
     * tenant-config gap, not a runtime error).
     */
    private boolean bumpYtdForType(UUID employeeId, TaxType type, BigDecimal amount, LocalDate asOfDate) {
        Optional<TaxWithholding> row = taxWithholdingRepository
            .findAll(
                (Specification<TaxWithholding>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.equal(root.get("type"), type),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate))
                    ),
                // Deterministically pick which active row receives the YTD bump when more
                // than one matches — latest-starting wins, id tiebreak — instead of arbitrary.
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("effectiveFrom"), Sort.Order.asc("id")))
            )
            .stream()
            .findFirst();
        if (row.isEmpty()) {
            log.debug(
                "Post YTD update: no active TaxWithholding row for employee {} type {}; {} not added to YTD ledger",
                employeeId,
                type,
                fmt(amount)
            );
            return false;
        }
        TaxWithholding w = row.orElseThrow();
        BigDecimal current = w.getYearToDateAmount() == null ? BigDecimal.ZERO : w.getYearToDateAmount();
        w.setYearToDateAmount(current.add(amount));
        taxWithholdingRepository.save(w);
        return true;
    }

    /**
     * Gets a summary of a payroll run with aggregated statistics.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PayrollRunSummaryResponse getPayrollRunSummary(UUID runId) {
        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));

        List<PayrollResult> results = resultsWithDepartments(runId);

        // P3.4 — with a reporting currency every total is in it (reporting_* columns); without
        // one, each currency the employees are paid in gets its own totals, and the single
        // totals exist only when there is just one.
        boolean useReporting = run.getReportingCurrency() != null;
        List<PayrollCurrencyTotals> byCurrency = totalsByCurrency(run, results);
        PayrollCurrencyTotals total = singleTotal(run, byCurrency);

        // Breakdown by component, in each line's own currency (the employee's: lines are never
        // converted). One query for every line in the run, instead of a per-result query.
        Map<List<String>, BigDecimal> earningTotals = new LinkedHashMap<>();
        Map<List<String>, BigDecimal> deductionTotals = new LinkedHashMap<>();
        List<PayrollLine> allLines = payrollLineRepository.findAll(
            (Specification<PayrollLine>) (root, query, cb) -> cb.equal(root.get("result").get("run").get("id"), runId)
        );
        for (PayrollLine line : allLines) {
            List<String> key = List.of(line.displayName(), currencyOf(line.getResult()));
            if (line.getComponent().getKind() == Kind.EARNING) {
                earningTotals.merge(key, line.getAmount(), BigDecimal::add);
            } else if (line.getComponent().getKind() == Kind.DEDUCTION) {
                deductionTotals.merge(key, line.getAmount().abs(), BigDecimal::add);
            }
        }
        List<PayrollRunSummaryResponse.ComponentTotal> earnings = componentTotals(earningTotals);
        List<PayrollRunSummaryResponse.ComponentTotal> deductions = componentTotals(deductionTotals);
        String currencyCode = total != null ? total.currencyCode() : null;
        boolean linesInCurrency = Stream.concat(earnings.stream(), deductions.stream()).allMatch(c -> c.currencyCode().equals(currencyCode)
        );

        Map<PayrollResult, String> departments = departmentsOf(results);
        return new PayrollRunSummaryResponse(
            runId,
            run.getPeriod().getCode(),
            results.size(),
            results.size(),
            (int) payrollRunIssueRepository.countByRunId(runId),
            total != null ? total.totalGross() : null,
            total != null ? total.totalDeductions() : null,
            total != null ? total.totalNet() : null,
            total != null ? total.totalEmployerCost() : null,
            // Employer cost already includes gross pay (gross + employer charges + benefits), so
            // it IS the total cost of the payroll — adding net on top would count wages twice.
            total != null ? total.totalEmployerCost() : null,
            currencyCode,
            byCurrency,
            linesInCurrency ? componentMap(earnings) : Map.of(),
            linesInCurrency ? componentMap(deductions) : Map.of(),
            earnings,
            deductions,
            departmentBreakdown(results, run, total == null, departments),
            // Pay in different currencies can't be ranked against each other.
            total != null ? topEarners(results, run, departments) : List.of(),
            total != null ? comparisonWithPrevious(run, results.size(), total) : null
        );
    }

    /** A run's results with employee and department loaded in the same query (summary breakdowns). */
    private List<PayrollResult> resultsWithDepartments(UUID runId) {
        return payrollResultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> {
                if (query != null && PayrollResult.class.equals(query.getResultType())) {
                    root
                        .fetch("employee", jakarta.persistence.criteria.JoinType.LEFT)
                        .fetch("department", jakarta.persistence.criteria.JoinType.LEFT);
                }
                return cb.equal(root.get("run").get("id"), runId);
            }
        );
    }

    /** The first throwable of {@code type} in {@code e}'s cause chain (the per-employee transaction may wrap it), or null. */
    private static <T extends Throwable> T causeOfType(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (type.isInstance(t)) return type.cast(t);
        }
        return null;
    }

    private static BigDecimal grossOf(PayrollResult r, boolean reporting) {
        return reporting ? coalesce(r.getReportingGross(), r.getGross()) : r.getGross();
    }

    private static BigDecimal netOf(PayrollResult r, boolean reporting) {
        return reporting ? coalesce(r.getReportingNet(), r.getNet()) : r.getNet();
    }

    /** Each result's department for the run's period (not today's: a later transfer must not regroup a past run). */
    private Map<PayrollResult, String> departmentsOf(List<PayrollResult> results) {
        EmployeeRoleHistory.Lookup roles = roleHistory.lookup(results.stream().map(PayrollResult::getEmployee).toList());
        Map<PayrollResult, String> departments = new java.util.IdentityHashMap<>();
        for (PayrollResult r : results) {
            String name = r.getPayrollPeriod() != null ? roles.at(r.getEmployee(), r.getPayrollPeriod().getEndDate()).department() : null;
            departments.put(r, name != null ? name : "—");
        }
        return departments;
    }

    /**
     * Headcount, gross, net and average gross per department, largest gross first. With {@code
     * byCurrency} each department is split by the currency its people are paid in, keyed
     * "department · currency".
     */
    private Map<String, PayrollRunSummaryResponse.DepartmentPayrollSummary> departmentBreakdown(
        List<PayrollResult> results,
        PayrollRun run,
        boolean byCurrency,
        Map<PayrollResult, String> departments
    ) {
        boolean reporting = run.getReportingCurrency() != null;
        Map<List<String>, List<PayrollResult>> groups = results
            .stream()
            .collect(
                Collectors.groupingBy(
                    r -> byCurrency ? List.of(departments.get(r), totalsCurrencyOf(run, r)) : List.of(departments.get(r)),
                    LinkedHashMap::new,
                    Collectors.toList()
                )
            );
        Map<String, PayrollRunSummaryResponse.DepartmentPayrollSummary> out = new LinkedHashMap<>();
        groups
            .entrySet()
            .stream()
            .map(e -> {
                BigDecimal gross = e.getValue().stream().map(r -> grossOf(r, reporting)).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal net = e.getValue().stream().map(r -> netOf(r, reporting)).reduce(BigDecimal.ZERO, BigDecimal::add);
                int count = e.getValue().size();
                return new PayrollRunSummaryResponse.DepartmentPayrollSummary(
                    e.getKey().get(0),
                    totalsCurrencyOf(run, e.getValue().get(0)),
                    count,
                    gross,
                    net,
                    gross.divide(BigDecimal.valueOf(count), MONEY_SCALE, MONEY_ROUNDING)
                );
            })
            .sorted(Comparator.comparing(PayrollRunSummaryResponse.DepartmentPayrollSummary::totalGross).reversed())
            .forEach(d -> out.put(byCurrency ? d.departmentName() + " · " + d.currencyCode() : d.departmentName(), d));
        return out;
    }

    private List<PayrollRunSummaryResponse.EmployeePayrollSummary> topEarners(
        List<PayrollResult> results,
        PayrollRun run,
        Map<PayrollResult, String> departments
    ) {
        boolean reporting = run.getReportingCurrency() != null;
        return results
            .stream()
            .sorted(Comparator.comparing((PayrollResult r) -> grossOf(r, reporting)).reversed())
            .limit(5)
            .map(r ->
                new PayrollRunSummaryResponse.EmployeePayrollSummary(
                    r.getEmployee().getId(),
                    r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName(),
                    departments.get(r),
                    totalsCurrencyOf(run, r),
                    grossOf(r, reporting),
                    netOf(r, reporting)
                )
            )
            .toList();
    }

    /**
     * A run's totals per currency, largest headcount first: one entry in the reporting currency when
     * the run has one (a result not yet converted counts at its native amount, as before), otherwise
     * one per currency its employees are paid in. Amounts in different currencies are never added.
     */
    static List<PayrollCurrencyTotals> totalsByCurrency(PayrollRun run, List<PayrollResult> results) {
        boolean reporting = run.getReportingCurrency() != null;
        Map<String, PayrollCurrencyTotals> byCode = new LinkedHashMap<>();
        for (PayrollResult r : results) {
            String code = totalsCurrencyOf(run, r);
            PayrollCurrencyTotals one = new PayrollCurrencyTotals(
                code,
                1,
                grossOf(r, reporting),
                reporting ? coalesce(r.getReportingTotalDeductions(), r.getTotalDeductions()) : r.getTotalDeductions(),
                netOf(r, reporting),
                reporting ? coalesce(r.getReportingEmployerCost(), r.getEmployerCost()) : r.getEmployerCost()
            );
            byCode.merge(code, one, PayrollCurrencyTotals::plus);
        }
        return byCode
            .values()
            .stream()
            .sorted(
                Comparator.comparingInt(PayrollCurrencyTotals::employeeCount).reversed().thenComparing(PayrollCurrencyTotals::currencyCode)
            )
            .toList();
    }

    /**
     * The run's single totals: its only currency's, zero for a run without results, or null when its
     * employees are paid in several currencies and it has no reporting currency.
     */
    static PayrollCurrencyTotals singleTotal(PayrollRun run, List<PayrollCurrencyTotals> byCurrency) {
        if (byCurrency.size() > 1) return null;
        if (byCurrency.size() == 1) return byCurrency.get(0);
        return PayrollCurrencyTotals.empty(run.getReportingCurrency() != null ? run.getReportingCurrency().getCode().getCode() : null);
    }

    /** The currency a result counts in towards the run's totals: the reporting one, or its own. */
    private static String totalsCurrencyOf(PayrollRun run, PayrollResult r) {
        return run.getReportingCurrency() != null ? run.getReportingCurrency().getCode().getCode() : currencyOf(r);
    }

    private static String currencyOf(PayrollResult r) {
        return r.getCurrency().getCode().getCode();
    }

    /** Component totals keyed by (name, currency), largest first. */
    private static List<PayrollRunSummaryResponse.ComponentTotal> componentTotals(Map<List<String>, BigDecimal> totals) {
        return totals
            .entrySet()
            .stream()
            .map(e -> new PayrollRunSummaryResponse.ComponentTotal(e.getKey().get(0), e.getKey().get(1), e.getValue()))
            .sorted(Comparator.comparing(PayrollRunSummaryResponse.ComponentTotal::amount).reversed())
            .toList();
    }

    /** By component name, for a run whose lines are all in one currency. */
    private static Map<String, BigDecimal> componentMap(List<PayrollRunSummaryResponse.ComponentTotal> totals) {
        Map<String, BigDecimal> out = new HashMap<>();
        totals.forEach(c -> out.merge(c.name(), c.amount(), BigDecimal::add));
        return out;
    }

    /** Against the latest posted run of an earlier period, if any and in the same single currency. */
    private PayrollRunSummaryResponse.ComparisonWithPreviousPeriod comparisonWithPrevious(
        PayrollRun run,
        int employees,
        PayrollCurrencyTotals total
    ) {
        BigDecimal gross = total.totalGross();
        BigDecimal net = total.totalNet();
        java.time.LocalDate start = run.getPeriod().getStartDate();
        List<PayrollRun> earlier = payrollRunRepository.findAll(
            (Specification<PayrollRun>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("status"), RunStatus.POSTED),
                    cb.lessThan(root.get("period").get("startDate"), start),
                    cb.notEqual(root.get("id"), run.getId())
                ),
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "period.startDate")
        );
        if (earlier.isEmpty()) {
            return null;
        }
        PayrollRun previousRun = earlier.get(0);
        List<PayrollResult> previous = payrollResultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), previousRun.getId())
        );
        PayrollCurrencyTotals previousTotal = singleTotal(previousRun, totalsByCurrency(previousRun, previous));
        if (previousTotal == null || !Objects.equals(previousTotal.currencyCode(), total.currencyCode())) {
            return null;
        }
        BigDecimal prevGross = previousTotal.totalGross();
        BigDecimal prevNet = previousTotal.totalNet();
        return new PayrollRunSummaryResponse.ComparisonWithPreviousPeriod(
            prevGross,
            gross.subtract(prevGross),
            percentChange(prevGross, gross),
            prevNet,
            net.subtract(prevNet),
            percentChange(prevNet, net),
            previous.size(),
            employees - previous.size()
        );
    }

    private static BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        return from.signum() == 0
            ? BigDecimal.ZERO
            : to
                .subtract(from)
                .divide(from, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, java.math.RoundingMode.HALF_UP);
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
            // Working people, plus anyone who left during or after this period (their final pay);
            // not someone who only starts after the period ends — they had no pay in it.
            return employeeRepository.findAll(
                (Specification<Employee>) (root, query, cb) -> {
                    var working = root.get("status").in(com.humano.domain.enumeration.hr.EmployeeStatus.WORKING);
                    if (period == null || period.getStartDate() == null) {
                        return working;
                    }
                    var employed = cb.or(
                        working,
                        cb.and(
                            cb.equal(root.get("status"), com.humano.domain.enumeration.hr.EmployeeStatus.TERMINATED),
                            cb.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), period.getStartDate())
                        )
                    );
                    if (period.getEndDate() == null) {
                        return employed;
                    }
                    return cb.and(
                        employed,
                        cb.or(cb.isNull(root.get("startDate")), cb.lessThanOrEqualTo(root.<LocalDate>get("startDate"), period.getEndDate()))
                    );
                }
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
                    ),
                // Push ORDER BY + LIMIT 1 into SQL instead of loading every active row and
                // taking an arbitrary first. When overlapping compensations are active, the
                // latest-starting one wins, with id as a stable tiebreaker — deterministic.
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("effectiveFrom"), Sort.Order.asc("id")))
            )
            .stream()
            .findFirst()
            .orElse(null);
    }

    private BigDecimal calculateBaseSalary(Compensation compensation, PayrollPeriod period, OrganizationSettings settings) {
        return switch (compensation.getBasis()) {
            case MONTHLY -> compensation.getBaseAmount();
            case ANNUAL -> compensation.getBaseAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            // HOURLY → monthly via the company-configured standard monthly hours (default 160).
            case HOURLY -> compensation.getBaseAmount().multiply(settings.getStandardMonthlyHours());
            default -> compensation.getBaseAmount();
        };
    }

    /**
     * Current company payroll policy, or transient defaults (8h/40h/160h, 1.5× OT,
     * MONTHLY) when none is saved — so behaviour is unchanged until a tenant sets it.
     * Read per-employee because each calculation runs in its own {@code REQUIRES_NEW} tx.
     */
    private OrganizationSettings resolveSettings() {
        return organizationSettingsRepository.findAll().stream().findFirst().orElseGet(OrganizationSettings::new);
    }

    private List<PayrollInput> getPayrollInputs(UUID employeeId, UUID periodId) {
        return payrollInputRepository.findAll(
            (Specification<PayrollInput>) (root, query, cb) ->
                cb.and(cb.equal(root.get("employee").get("id"), employeeId), cb.equal(root.get("period").get("id"), periodId))
        );
    }

    /**
     * Builds the variable map passed to {@link PayrollFormulaEngine#evaluateFormula}.
     *
     * <p><strong>Whitelist sync invariant (P3.6).</strong> Every key set here MUST be
     * either in {@code PayrollFormulaEngine.ALLOWED_VARIABLE_NAMES} or match the
     * uppercase {@code PayComponentCode}-shaped pattern; otherwise the engine
     * silently drops it at evaluation time and tenant formulas referencing the
     * variable see {@code null}. Keep the two lists aligned when adding new context
     * keys.
     */
    private Map<String, Object> buildCalculationContext(
        Employee employee,
        Compensation compensation,
        PayrollPeriod period,
        List<PayrollInput> inputs,
        BigDecimal baseSalary,
        OrganizationSettings settings,
        List<TaxWithholding> activeWithholdings
    ) {
        Map<String, Object> context = new HashMap<>();
        context.put("employeeId", employee.getId());
        context.put("baseSalary", baseSalary);
        context.put("grossSalary", baseSalary);
        context.put("periodStartDate", period.getStartDate());
        context.put("periodEndDate", period.getEndDate());
        context.put("workDays", calculateWorkDays(period));

        // Year-to-date contribution per withholding type, as #YTD_<TYPE> (e.g. #YTD_SOCIAL_SECURITY).
        // The value is the running total carried on the employee's active TaxWithholding row, which is
        // advanced only when a run is POSTED (§P3.3), so during calc it reflects prior posted periods.
        // This lets a PayRule formula enforce an annual ceiling across consecutive runs — e.g. a capped
        // social-security contribution: min(rate% * grossSalary, max(0, SOCIAL_SECURITY_CAP - YTD)).
        // Matches PayrollFormulaEngine.ALLOWED_DYNAMIC_NAME, so no static whitelist entry is needed.
        Map<TaxType, BigDecimal> ytdByType = new EnumMap<>(TaxType.class);
        for (TaxWithholding w : activeWithholdings) {
            ytdByType.merge(w.getType(), nz(w.getYearToDateAmount()), BigDecimal::add);
        }
        ytdByType.forEach((type, ytd) -> context.put("YTD_" + type.name(), ytd));

        // Company payroll-policy defaults, so tenant formulas can reference the configured
        // overtime multiplier / standard hours instead of hardcoding them. Keep these keys in
        // sync with PayrollFormulaEngine.ALLOWED_VARIABLE_NAMES (the whitelist invariant below).
        context.put("standardHoursPerDay", settings.getStandardHoursPerDay());
        context.put("standardHoursPerWeek", settings.getStandardHoursPerWeek());
        context.put("standardMonthlyHours", settings.getStandardMonthlyHours());
        context.put("overtimeMultiplier", settings.getDefaultOvertimeMultiplier());

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
            PayrollInput input = directInput.orElseThrow();
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

    /**
     * Enforces who may approve a payroll run. Two controls, applied in order:
     *
     * <ol>
     *   <li><strong>Permission (hard, never bypassable).</strong> The named approver must hold
     *       at least one authority that grants {@link PermissionsConstants#APPROVE_PAYROLL}.
     *       The class-level {@code @RequirePayrollAdmin} on the REST resource only checks the
     *       <em>caller</em>; this checks the <em>approver of record</em>, so a privileged caller
     *       cannot record an approval against someone who lacks the authority. Fails closed.</li>
     *   <li><strong>Segregation of duties (self-approval).</strong> The approver may not be the
     *       same person who created the run (compared by audit login). This is overridable with
     *       {@code forceApproval=true} — e.g. a single-operator tenant — but the override is
     *       logged at WARN so it leaves a trail. The permission control above is <em>not</em>
     *       overridable.</li>
     * </ol>
     *
     * @throws BusinessRuleViolationException when the approver lacks the approval permission,
     *         or self-approves without {@code forceApproval}.
     */
    private void validateApprovalAuthority(Employee approver, PayrollRun run, boolean forceApproval) {
        boolean hasApprovalPermission = approver
            .getAuthorities()
            .stream()
            .anyMatch(authority -> authorityPermissionService.hasPermission(authority.getName(), PermissionsConstants.APPROVE_PAYROLL));
        if (!hasApprovalPermission) {
            throw new BusinessRuleViolationException(
                "Employee " + approver.getId() + " is not authorized to approve payroll runs (missing APPROVE_PAYROLL permission)."
            );
        }

        String creator = run.getCreatedBy();
        String approverLogin = approver.getLogin();
        boolean selfApproval = creator != null && approverLogin != null && creator.equalsIgnoreCase(approverLogin);
        if (selfApproval) {
            if (!forceApproval) {
                throw new BusinessRuleViolationException(
                    "Approver may not approve a payroll run they created (segregation of duties). " +
                    "Set forceApproval to override on single-operator tenants."
                );
            }
            log.warn(
                "Self-approval override on run {}: approver {} created the run; proceeding because forceApproval=true",
                run.getId(),
                approver.getId()
            );
        }

        log.debug("Approval authority validated for {} on run {}", approver.getId(), run.getId());
    }

    /** Bumped whenever the hash *format* changes so old runs don't collide with new ones.
     *  v2 (P3.4): added {@code reportingCurrencyId} as the final pipe-delimited field. */
    private static final int HASH_VERSION = 3;

    private static final String HASH_DELIM = "|";

    /**
     * SHA-256 idempotency hash  over the canonical run inputs. Pure function of its
     * arguments &mdash; no clock, no randomness. Two calls with identical inputs yield
     * identical hashes; any change to employee set / pay-rule version / tax-bracket
     * version / reporting currency invalidates the hash.
     */
    private String generateIdempotencyHash(
        UUID periodId,
        String scope,
        List<UUID> sortedEmployeeIds,
        Instant payRuleVersion,
        Instant taxBracketVersion,
        String templateFingerprint,
        UUID reportingCurrencyId
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
        payload.append(HASH_DELIM);
        payload.append(templateFingerprint == null ? "" : templateFingerprint);
        payload.append(HASH_DELIM);
        payload.append(reportingCurrencyId == null ? "0" : reportingCurrencyId.toString());
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

    /** Whether the employee was employed from the period's first day through its last. */
    private static boolean employedThroughout(Employee employee, PayrollPeriod period) {
        boolean startedBefore = employee.getStartDate() == null || !employee.getStartDate().isAfter(period.getStartDate());
        boolean endedAfter = employee.getEndDate() == null || !employee.getEndDate().isBefore(period.getEndDate());
        return startedBefore && endedAfter;
    }

    /** Human-readable money formatter used inside {@code explain} text. */
    private static String fmt(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING).toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** First non-null of (a, b), or BigDecimal.ZERO if both null. Used by summary methods'
     *  reporting-vs-native fallback. */
    private static BigDecimal coalesce(BigDecimal a, BigDecimal b) {
        if (a != null) return a;
        if (b != null) return b;
        return BigDecimal.ZERO;
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
     * P3.4 — converts {@code result}'s native totals into {@code run.reportingCurrency} and
     * persists them on the result alongside the rate + rate-date used. No-op when:
     *
     * <ul>
     *   <li>{@code run.reportingCurrency} is null (single-currency run),</li>
     *   <li>the employee's native currency equals the reporting currency (rate=1, captured),</li>
     * </ul>
     *
     * <p>The rate is resolved once via
     * {@link ExchangeRateService#getReportingRate} at {@code period.paymentDate}, with a
     * most-recent-before fallback bounded by {@link #maxExchangeRateStalenessDays}. All
     * four totals are multiplied by the SAME rate so reporting figures stay internally
     * consistent (i.e. {@code reportingNet = reportingGross − reportingTotalDeductions}
     * still holds to the precision of the conversion). Throws
     * {@link BusinessRuleViolationException} on stale/missing rate; the caller's
     * per-employee try/catch in {@link #calculatePayroll} surfaces the failure as a
     * {@code PayrollValidationError} so the run completes for other employees.
     */
    private void applyReportingConversion(
        PayrollRun run,
        PayrollResult result,
        PayrollPeriod period,
        ExchangeRateService.ReportingRate snapshot,
        BigDecimal grossPay,
        BigDecimal totalDeductions,
        BigDecimal netPay,
        BigDecimal employerCost
    ) {
        Currency reporting = run.getReportingCurrency();
        if (reporting == null || snapshot == null) {
            return;
        }
        BigDecimal rate = snapshot.rate();
        result.setReportingGross(grossPay.multiply(rate).setScale(MONEY_SCALE, MONEY_ROUNDING));
        result.setReportingTotalDeductions(totalDeductions.multiply(rate).setScale(MONEY_SCALE, MONEY_ROUNDING));
        result.setReportingNet(netPay.multiply(rate).setScale(MONEY_SCALE, MONEY_ROUNDING));
        result.setReportingEmployerCost(employerCost.multiply(rate).setScale(MONEY_SCALE, MONEY_ROUNDING));
        result.setExchangeRate(rate);
        result.setExchangeRateDate(snapshot.rateDate());
        if (!period.getPaymentDate().equals(snapshot.rateDate())) {
            log.info(
                "Reporting conversion fallback for employee {}: paymentDate={} but rate from {} (≤{} days stale)",
                result.getEmployee().getId(),
                period.getPaymentDate(),
                snapshot.rateDate(),
                maxExchangeRateStalenessDays
            );
        }
    }

    /**
     * Income-tax {@link TaxCode}s evaluated through the progressive-bracket path in step 7, national
     * first. PIT is the only one with seeded data today; state/local are evaluated when (and only when)
     * a country has brackets for them, so PIT-only countries are unaffected.
     */
    private static final List<TaxCode> INCOME_TAX_CODES = List.of(TaxCode.PIT, TaxCode.STATE_PIT, TaxCode.LOCAL_PIT);

    /** Maps each income-tax {@link TaxCode} to the {@link TaxType} its payroll line is tagged with. */
    private static final Map<TaxCode, TaxType> INCOME_TAX_LEDGER_TYPE = Map.of(
        TaxCode.PIT,
        TaxType.INCOME_TAX,
        TaxCode.STATE_PIT,
        TaxType.STATE_INCOME_TAX,
        TaxCode.LOCAL_PIT,
        TaxType.LOCAL_INCOME_TAX
    );

    /** One computed income-tax charge: the source code, the ledger {@link TaxType}, the amount, and the bracket count (for the explain line). */
    record IncomeTaxComponent(TaxCode code, TaxType ledgerType, BigDecimal amount, int bracketCount) {}

    /**
     * Runs the progressive-bracket calc for each {@link #INCOME_TAX_CODES} code that has active brackets,
     * returning one {@link IncomeTaxComponent} per code that yields a positive charge (in national-first
     * order). Codes with no brackets, or a zero/negative computed tax, are skipped — so a PIT-only country
     * returns a single {@code INCOME_TAX} entry identical to the pre-iteration behaviour.
     *
     * <p>Pure over its injected {@code bracketLookup} / {@code progressiveTax} functions so it is unit
     * testable without the payroll/DB stack.
     */
    static List<IncomeTaxComponent> computeIncomeTaxes(
        BigDecimal taxableIncome,
        Function<TaxCode, List<TaxBracket>> bracketLookup,
        BiFunction<BigDecimal, List<TaxBracket>, BigDecimal> progressiveTax
    ) {
        List<IncomeTaxComponent> result = new ArrayList<>();
        for (TaxCode code : INCOME_TAX_CODES) {
            List<TaxBracket> brackets = bracketLookup.apply(code);
            if (brackets == null || brackets.isEmpty()) {
                continue;
            }
            BigDecimal tax = progressiveTax.apply(taxableIncome, brackets);
            if (tax != null && tax.signum() > 0) {
                result.add(new IncomeTaxComponent(code, INCOME_TAX_LEDGER_TYPE.get(code), tax, brackets.size()));
            }
        }
        return result;
    }

    /**
     * Active {@link TaxWithholding} rows for an employee at the given as-of date. Sorted
     * by (type, id) for determinism.
     */
    private List<TaxWithholding> activeWithholdingsForPeriod(Employee employee, LocalDate asOfDate) {
        return taxWithholdingRepository
            .findAll(
                (Specification<TaxWithholding>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employee.getId()),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate))
                    )
            )
            .stream()
            .sorted(Comparator.comparing(TaxWithholding::getType).thenComparing(TaxWithholding::getId))
            .toList();
    }

    /**
     * Computes the withheld amount for a {@link TaxWithholding} row: {@code rate% × gross}.
     * The rate is stored as a percentage 0..100 on the entity (see
     * {@link TaxWithholding#getRate()}).
     */
    private BigDecimal computeWithholdingAmount(TaxWithholding w, BigDecimal gross) {
        if (w.getRate() == null || w.getRate().signum() == 0 || gross == null || gross.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = w.getRate().divide(BigDecimal.valueOf(100), RATE_SCALE, MONEY_ROUNDING);
        return gross.multiply(ratio).setScale(MONEY_SCALE, MONEY_ROUNDING);
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

    /**
     * Payroll run history, newest pay period first. Totals for the whole page come from one
     * GROUP BY over the results instead of loading every run's results.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PayrollRunResponse> listRuns(Pageable pageable) {
        Pageable sorted = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "period.startDate", "createdDate")
        );
        Page<PayrollRun> page = payrollRunRepository.findHistory(sorted);
        // One row per run and native currency, already in the reporting currency when the run has one.
        Map<UUID, List<RunTotalsRow>> rows = new HashMap<>();
        if (page.hasContent()) {
            for (RunTotalsRow row : payrollResultRepository.totalsForRuns(page.map(PayrollRun::getId).getContent())) {
                rows.computeIfAbsent(row.runId(), k -> new ArrayList<>()).add(row);
            }
        }
        return page.map(run -> {
            Map<String, PayrollCurrencyTotals> byCode = new LinkedHashMap<>();
            for (RunTotalsRow t : rows.getOrDefault(run.getId(), List.of())) {
                String code = run.getReportingCurrency() != null ? run.getReportingCurrency().getCode().getCode() : t.currency().getCode();
                PayrollCurrencyTotals one = new PayrollCurrencyTotals(
                    code,
                    (int) t.employees(),
                    t.gross(),
                    t.deductions(),
                    t.net(),
                    t.employerCost()
                );
                byCode.merge(code, one, PayrollCurrencyTotals::plus);
            }
            List<PayrollCurrencyTotals> byCurrency = byCode
                .values()
                .stream()
                .sorted(
                    Comparator.comparingInt(PayrollCurrencyTotals::employeeCount)
                        .reversed()
                        .thenComparing(PayrollCurrencyTotals::currencyCode)
                )
                .toList();
            PayrollCurrencyTotals total = singleTotal(run, byCurrency);
            PayrollPeriod period = run.getPeriod();
            return new PayrollRunResponse(
                run.getId(),
                period.getId(),
                period.getCode(),
                period.getStartDate(),
                period.getEndDate(),
                period.getPaymentDate(),
                run.getScope(),
                run.getStatus(),
                byCurrency.stream().mapToInt(PayrollCurrencyTotals::employeeCount).sum(),
                total != null ? total.totalGross() : null,
                total != null ? total.totalDeductions() : null,
                total != null ? total.totalNet() : null,
                total != null ? total.totalEmployerCost() : null,
                total != null ? total.currencyCode() : null,
                byCurrency,
                run.getApprovedAt(),
                run.getApprovedBy() != null ? run.getApprovedBy().getFirstName() + " " + run.getApprovedBy().getLastName() : null,
                Collections.emptyList(),
                run.getIssueOverrideReason(),
                run.getCreatedDate() != null ? OffsetDateTime.from(run.getCreatedDate().atOffset(java.time.ZoneOffset.UTC)) : null,
                run.getCreatedBy()
            );
        });
    }

    /** One payroll run with its current status and totals. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PayrollRunResponse getRun(UUID runId) {
        PayrollRun run = payrollRunRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("PayrollRun", runId));
        return toRunResponse(run);
    }

    private PayrollRunResponse toRunResponse(PayrollRun run) {
        PayrollPeriod period = run.getPeriod();

        // Get aggregated totals
        List<PayrollResult> results = payrollResultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> cb.equal(root.get("run").get("id"), run.getId())
        );

        // P3.4 — per currency, never summed across currencies; see totalsByCurrency.
        List<PayrollCurrencyTotals> byCurrency = totalsByCurrency(run, results);
        PayrollCurrencyTotals total = singleTotal(run, byCurrency);

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
            total != null ? total.totalGross() : null,
            total != null ? total.totalDeductions() : null,
            total != null ? total.totalNet() : null,
            total != null ? total.totalEmployerCost() : null,
            total != null ? total.currencyCode() : null,
            byCurrency,
            run.getApprovedAt(),
            run.getApprovedBy() != null ? run.getApprovedBy().getFirstName() + " " + run.getApprovedBy().getLastName() : null,
            issuesOf(run),
            run.getIssueOverrideReason(),
            run.getCreatedDate() != null ? OffsetDateTime.from(run.getCreatedDate().atOffset(java.time.ZoneOffset.UTC)) : null,
            run.getCreatedBy()
        );
    }
}
