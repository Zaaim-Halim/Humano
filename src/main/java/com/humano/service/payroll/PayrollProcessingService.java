package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.*;
import com.humano.domain.payroll.*;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.ApprovePayrollRunRequest;
import com.humano.dto.payroll.request.InitiatePayrollRunRequest;
import com.humano.dto.payroll.request.RecalculatePayrollRequest;
import com.humano.dto.payroll.response.PayrollRunResponse;
import com.humano.dto.payroll.response.PayrollRunSummaryResponse;
import com.humano.repository.payroll.*;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for payroll processing including run initiation, calculation,
 * approval workflow, and posting. Orchestrates the entire payroll lifecycle.
 */
@Service
@Transactional
public class PayrollProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PayrollProcessingService.class);

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
        this.formulaEngine = formulaEngine;
        this.deductionService = deductionService;
        this.bonusService = bonusService;
    }

    /**
     * Initiates a new payroll run for a specific period.
     */
    public PayrollRunResponse initiatePayrollRun(InitiatePayrollRunRequest request) {
        log.info("Initiating payroll run for period: {}", request.periodId());

        PayrollPeriod period = payrollPeriodRepository
            .findById(request.periodId())
            .orElseThrow(() -> new EntityNotFoundException("PayrollPeriod", request.periodId()));

        if (period.isClosed()) {
            throw new BusinessRuleViolationException("Cannot create payroll run for closed period");
        }

        // Check for existing draft runs
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

        // Determine scope
        String scope = request.scope() != null ? request.scope().name() : "ALL";

        // Generate idempotency hash
        String hash = generateIdempotencyHash(period.getId(), scope);

        PayrollRun run = new PayrollRun();
        run.setPeriod(period);
        run.setScope(scope);
        run.setStatus(RunStatus.DRAFT);
        run.setHash(hash);

        run = payrollRunRepository.save(run);
        log.info("Created payroll run {} with status DRAFT", run.getId());

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
     * Calculates payroll for a single employee.
     */
    private PayrollResult calculateEmployeePayroll(PayrollRun run, PayrollPeriod period, Employee employee) {
        log.debug("Calculating payroll for employee: {}", employee.getId());

        // Get or create payroll result
        Optional<PayrollResult> existingResult = payrollResultRepository
            .findAll(
                (Specification<PayrollResult>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("run").get("id"), run.getId()), cb.equal(root.get("employee").get("id"), employee.getId()))
            )
            .stream()
            .findFirst();

        PayrollResult result;
        if (existingResult.isPresent()) {
            result = existingResult.get();
            // Clear existing lines for recalculation
            final UUID resultId = result.getId();
            List<PayrollLine> existingLines = payrollLineRepository.findAll(
                (Specification<PayrollLine>) (root, query, cb) -> cb.equal(root.get("result").get("id"), resultId)
            );
            payrollLineRepository.deleteAll(existingLines);
        } else {
            result = new PayrollResult();
            result.setRun(run);
            result.setEmployee(employee);
            result.setPayrollPeriod(period);
        }

        // Get active compensation
        Compensation compensation = findActiveCompensation(employee.getId(), period.getEndDate());
        if (compensation == null) {
            throw new BusinessRuleViolationException("No active compensation found for employee " + employee.getId());
        }

        result.setCurrency(compensation.getCurrency());

        // Calculate base salary
        BigDecimal baseSalary = calculateBaseSalary(compensation, period);

        // Get payroll inputs for the period
        List<PayrollInput> inputs = getPayrollInputs(employee.getId(), period.getId());

        // Build calculation context
        Map<String, Object> context = buildCalculationContext(employee, compensation, period, inputs, baseSalary);

        // Get and process pay components in order
        List<PayComponent> components = payComponentRepository.findAll();
        components.sort(Comparator.comparingInt(c -> c.getCalcPhase() != null ? c.getCalcPhase() : 999));

        BigDecimal totalEarnings = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalEmployerCharges = BigDecimal.ZERO;

        List<PayrollLine> lines = new ArrayList<>();
        int sequence = 1;

        for (PayComponent component : components) {
            try {
                BigDecimal amount = calculateComponent(component, context, inputs);
                if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                PayrollLine line = new PayrollLine();
                line.setResult(result);
                line.setComponent(component);
                line.setAmount(amount);
                line.setSequence(sequence++);

                // Set quantity and rate if available from inputs
                inputs
                    .stream()
                    .filter(i -> i.getComponent().getId().equals(component.getId()))
                    .findFirst()
                    .ifPresent(input -> {
                        line.setQuantity(input.getQuantity());
                        line.setRate(input.getRate());
                    });

                lines.add(line);

                // Categorize by kind
                switch (component.getKind()) {
                    case EARNING -> totalEarnings = totalEarnings.add(amount);
                    case DEDUCTION -> totalDeductions = totalDeductions.add(amount.abs());
                    case EMPLOYER_CHARGE -> totalEmployerCharges = totalEmployerCharges.add(amount);
                }

                // Update context with calculated value for dependent calculations
                context.put(component.getCode().name(), amount);
            } catch (Exception e) {
                log.warn("Error calculating component {} for employee {}: {}", component.getCode(), employee.getId(), e.getMessage());
            }
        }

        // Set result totals
        result.setGross(totalEarnings);
        result.setTotalDeductions(totalDeductions);
        result.setNet(totalEarnings.subtract(totalDeductions));
        result.setEmployerCost(totalEarnings.add(totalEmployerCharges));

        result = payrollResultRepository.save(result);

        // Save all lines
        for (PayrollLine line : lines) {
            line.setResult(result);
        }
        payrollLineRepository.saveAll(lines);

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

    private String generateIdempotencyHash(UUID periodId, String scope) {
        return periodId.toString() + "-" + scope + "-" + System.currentTimeMillis();
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
