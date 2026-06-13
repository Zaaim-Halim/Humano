package com.humano.service.payroll;

import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.CompensationSearchRequest;
import com.humano.dto.payroll.request.CreateCompensationRequest;
import com.humano.dto.payroll.request.SalaryAdjustmentRequest;
import com.humano.dto.payroll.response.CompensationResponse;
import com.humano.dto.payroll.response.SalaryHistoryResponse;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.payroll.CompensationRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.specification.CompensationSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.audit.AuditEventService;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing employee compensation including salary adjustments,
 * salary history tracking, and compensation analytics.
 */
@Service
@Transactional
public class CompensationService {

    private static final Logger log = LoggerFactory.getLogger(CompensationService.class);

    private final CompensationRepository compensationRepository;
    private final EmployeeRepository employeeRepository;
    private final PositionRepository positionRepository;
    private final CurrencyRepository currencyRepository;
    private final AuditEventService auditEventService;

    public CompensationService(
        CompensationRepository compensationRepository,
        EmployeeRepository employeeRepository,
        PositionRepository positionRepository,
        CurrencyRepository currencyRepository,
        AuditEventService auditEventService
    ) {
        this.compensationRepository = compensationRepository;
        this.employeeRepository = employeeRepository;
        this.positionRepository = positionRepository;
        this.currencyRepository = currencyRepository;
        this.auditEventService = auditEventService;
    }

    /**
     * Creates a new compensation record for an employee.
     * Automatically closes any overlapping compensation periods.
     */
    public CompensationResponse createCompensation(CreateCompensationRequest request) {
        log.debug("Creating compensation for employee: {}", request.employeeId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee", request.employeeId()));

        Position position = positionRepository
            .findById(request.positionId())
            .orElseThrow(() -> new EntityNotFoundException("Position", request.positionId()));

        Currency currency = currencyRepository
            .findById(request.currencyId())
            .orElseThrow(() -> new EntityNotFoundException("Currency", request.currencyId()));

        // Close any active compensation that overlaps with the new one
        closeOverlappingCompensations(employee.getId(), request.effectiveFrom());

        Compensation compensation = new Compensation();
        compensation.setEmployee(employee);
        compensation.setPosition(position);
        compensation.setBaseAmount(request.baseAmount());
        compensation.setBasis(request.basis());
        compensation.setCurrency(currency);
        compensation.setEffectiveFrom(request.effectiveFrom());
        compensation.setEffectiveTo(request.effectiveTo());

        compensation = compensationRepository.save(compensation);
        log.info("Created compensation {} for employee {}", compensation.getId(), employee.getId());

        return toResponse(compensation);
    }

    /**
     * Adjusts an employee's salary by either a fixed amount or percentage.
     * Creates a new compensation record and closes the previous one.
     */
    public CompensationResponse adjustSalary(SalaryAdjustmentRequest request) {
        log.debug("Adjusting salary for employee: {}", request.employeeId());

        Compensation currentCompensation = findActiveCompensation(request.employeeId(), request.effectiveFrom()).orElseThrow(() ->
            new BusinessRuleViolationException("No active compensation found for employee " + request.employeeId())
        );

        BigDecimal newAmount;
        if (request.newAmount() != null) {
            newAmount = request.newAmount();
        } else {
            // Calculate based on percentage adjustment
            BigDecimal adjustmentMultiplier = BigDecimal.ONE.add(
                request.adjustmentPercentage().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
            newAmount = currentCompensation.getBaseAmount().multiply(adjustmentMultiplier).setScale(2, RoundingMode.HALF_UP);
        }

        // Validate the new amount
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleViolationException("Salary cannot be negative after adjustment");
        }

        // Close current compensation
        currentCompensation.setEffectiveTo(request.effectiveFrom().minusDays(1));
        compensationRepository.save(currentCompensation);

        // Create new compensation
        Compensation newCompensation = new Compensation();
        newCompensation.setEmployee(currentCompensation.getEmployee());
        newCompensation.setPosition(currentCompensation.getPosition());
        newCompensation.setBaseAmount(newAmount);
        newCompensation.setBasis(request.newBasis() != null ? request.newBasis() : currentCompensation.getBasis());
        newCompensation.setCurrency(
            request.newCurrencyId() != null
                ? currencyRepository
                    .findById(request.newCurrencyId())
                    .orElseThrow(() -> new EntityNotFoundException("Currency", request.newCurrencyId()))
                : currentCompensation.getCurrency()
        );
        newCompensation.setEffectiveFrom(request.effectiveFrom());

        BigDecimal previousAmount = currentCompensation.getBaseAmount();
        newCompensation = compensationRepository.save(newCompensation);
        log.info("Adjusted salary for employee {} from {} to {}", request.employeeId(), previousAmount, newAmount);

        // P6.2 — explicit audit with before/after, since the aspect only sees
        // the after-state. Joins this @Transactional boundary; a rollback of
        // the salary change rolls back the audit row.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("before", previousAmount);
        payload.put("after", newAmount);
        payload.put("currency", newCompensation.getCurrency().getCode());
        payload.put("employeeId", request.employeeId().toString());
        payload.put("effectiveFrom", request.effectiveFrom().toString());
        if (request.adjustmentPercentage() != null) {
            payload.put("adjustmentPercentage", request.adjustmentPercentage());
        }
        auditEventService.record("COMPENSATION_ADJUSTED", "Compensation", newCompensation.getId().toString(), payload);

        return toResponse(newCompensation);
    }

    /**
     * Retrieves the complete salary history for an employee with trend analysis.
     */
    @Transactional(readOnly = true)
    public SalaryHistoryResponse getSalaryHistory(UUID employeeId) {
        log.debug("Fetching salary history for employee: {}", employeeId);

        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));

        List<Compensation> compensations = compensationRepository.findAll(
            (Specification<Compensation>) (root, query, cb) -> {
                query.orderBy(cb.asc(root.get("effectiveFrom")));
                return cb.equal(root.get("employee").get("id"), employeeId);
            }
        );

        if (compensations.isEmpty()) {
            return new SalaryHistoryResponse(
                employeeId,
                employee.getFirstName() + " " + employee.getLastName(),
                Collections.emptyList(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null
            );
        }

        List<SalaryHistoryResponse.SalaryChange> changes = new ArrayList<>();
        BigDecimal firstSalary = compensations.get(0).getBaseAmount();

        for (int i = 1; i < compensations.size(); i++) {
            Compensation prev = compensations.get(i - 1);
            Compensation curr = compensations.get(i);

            BigDecimal changeAmount = curr.getBaseAmount().subtract(prev.getBaseAmount());
            BigDecimal changePercentage = prev.getBaseAmount().compareTo(BigDecimal.ZERO) != 0
                ? changeAmount.divide(prev.getBaseAmount(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            changes.add(
                new SalaryHistoryResponse.SalaryChange(
                    curr.getId(),
                    prev.getBaseAmount(),
                    curr.getBaseAmount(),
                    curr.getBasis(),
                    curr.getCurrency().getCode().getCode(),
                    changeAmount,
                    changePercentage,
                    curr.getEffectiveFrom(),
                    null, // reason would come from audit log
                    curr.getCreatedBy()
                )
            );
        }

        Compensation latestCompensation = compensations.get(compensations.size() - 1);
        BigDecimal totalGrowthPercentage = firstSalary.compareTo(BigDecimal.ZERO) != 0
            ? latestCompensation
                .getBaseAmount()
                .subtract(firstSalary)
                .divide(firstSalary, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // Calculate average annual growth
        BigDecimal averageAnnualGrowth = calculateAverageAnnualGrowth(compensations);

        return new SalaryHistoryResponse(
            employeeId,
            employee.getFirstName() + " " + employee.getLastName(),
            changes,
            totalGrowthPercentage,
            averageAnnualGrowth,
            toResponse(latestCompensation)
        );
    }

    /**
     * Re-points the employee's active compensation at a new position effective
     * {@code effectiveDate}, preserving amount/basis/currency. Closes the
     * current row at {@code effectiveDate.minusDays(1)} and opens a new one.
     *
     * <p>No-op if no active compensation exists (just-hired, pre-payroll
     * employee) or if the active row already points at {@code newPositionId}.
     *
     * @return the newly-opened compensation row, or {@code Optional.empty()}
     *         when there was nothing to sync.
     */
    public Optional<Compensation> applyPositionChange(UUID employeeId, UUID newPositionId, LocalDate effectiveDate) {
        Optional<Compensation> currentOpt = findActiveCompensation(employeeId, effectiveDate);
        if (currentOpt.isEmpty()) {
            log.info("No active compensation for employee {} on {}; skipping position sync", employeeId, effectiveDate);
            return Optional.empty();
        }
        Compensation current = currentOpt.get();
        if (current.getPosition() != null && current.getPosition().getId().equals(newPositionId)) {
            return Optional.of(current);
        }

        Position newPosition = positionRepository
            .findById(newPositionId)
            .orElseThrow(() -> new EntityNotFoundException("Position", newPositionId));

        current.setEffectiveTo(effectiveDate.minusDays(1));
        compensationRepository.save(current);

        Compensation next = new Compensation();
        next.setEmployee(current.getEmployee());
        next.setPosition(newPosition);
        next.setBaseAmount(current.getBaseAmount());
        next.setBasis(current.getBasis());
        next.setCurrency(current.getCurrency());
        next.setEffectiveFrom(effectiveDate);
        next = compensationRepository.save(next);

        log.info("Synced compensation for employee {} to position {} effective {}", employeeId, newPositionId, effectiveDate);
        return Optional.of(next);
    }

    /**
     * Finds the active compensation for an employee on a specific date.
     */
    @Transactional(readOnly = true)
    public Optional<Compensation> findActiveCompensation(UUID employeeId, LocalDate asOfDate) {
        return compensationRepository
            .findAll(
                (Specification<Compensation>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate))
                    ),
                // Deterministic pick + LIMIT 1, consistent with the calc-path findActiveCompensation.
                // Compensation has no uniqueness on the active window, so overlapping rows would
                // otherwise make this arbitrary; latest-starting wins.
                PageRequest.of(0, 1, Sort.by(Sort.Order.desc("effectiveFrom"), Sort.Order.asc("id")))
            )
            .stream()
            .findFirst();
    }

    /**
     * Gets all compensations for employees in a specific department.
     */
    @Transactional(readOnly = true)
    public Page<CompensationResponse> getCompensationsByDepartment(UUID departmentId, Pageable pageable) {
        return compensationRepository
            .findAll(
                (Specification<Compensation>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("department").get("id"), departmentId),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), LocalDate.now()))
                    ),
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Calculates the compensation cost for a department or organization.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calculateCompensationCost(UUID departmentId, LocalDate asOfDate) {
        List<Compensation> activeCompensations = compensationRepository.findAll(
            (Specification<Compensation>) (root, query, cb) -> {
                List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate));
                predicates.add(cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate)));
                if (departmentId != null) {
                    predicates.add(cb.equal(root.get("employee").get("department").get("id"), departmentId));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }
        );

        BigDecimal totalMonthly = BigDecimal.ZERO;
        BigDecimal totalAnnual = BigDecimal.ZERO;

        for (Compensation comp : activeCompensations) {
            BigDecimal monthly = normalizeToMonthly(comp);
            totalMonthly = totalMonthly.add(monthly);
            totalAnnual = totalAnnual.add(monthly.multiply(BigDecimal.valueOf(12)));
        }

        Map<String, BigDecimal> result = new HashMap<>();
        result.put("totalMonthly", totalMonthly);
        result.put("totalAnnual", totalAnnual);
        result.put("employeeCount", BigDecimal.valueOf(activeCompensations.size()));
        result.put(
            "averageMonthly",
            activeCompensations.isEmpty()
                ? BigDecimal.ZERO
                : totalMonthly.divide(BigDecimal.valueOf(activeCompensations.size()), 2, RoundingMode.HALF_UP)
        );

        return result;
    }

    /**
     * Search compensations using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of compensation responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<CompensationResponse> searchCompensations(CompensationSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Compensations with criteria: {}", searchRequest);

        Specification<Compensation> specification = CompensationSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.positionId(),
            searchRequest.currencyId(),
            searchRequest.basis(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.effectiveFrom(),
            searchRequest.effectiveTo(),
            searchRequest.activeOnly(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return compensationRepository.findAll(specification, pageable).map(this::toResponse);
    }

    /**
     * Search compensations for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID to filter by
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of compensation responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<CompensationResponse> searchCompensationsByEmployee(
        UUID employeeId,
        CompensationSearchRequest searchRequest,
        Pageable pageable
    ) {
        log.debug("Request to search Compensations for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        Specification<Compensation> specification = CompensationSpecification.withCriteria(
            employeeId,
            searchRequest.positionId(),
            searchRequest.currencyId(),
            searchRequest.basis(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.effectiveFrom(),
            searchRequest.effectiveTo(),
            searchRequest.activeOnly(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return compensationRepository.findAll(specification, pageable).map(this::toResponse);
    }

    private void closeOverlappingCompensations(UUID employeeId, LocalDate newEffectiveFrom) {
        List<Compensation> overlapping = compensationRepository.findAll(
            (Specification<Compensation>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), newEffectiveFrom),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), newEffectiveFrom))
                )
        );

        for (Compensation comp : overlapping) {
            comp.setEffectiveTo(newEffectiveFrom.minusDays(1));
            compensationRepository.save(comp);
        }
    }

    private BigDecimal normalizeToMonthly(Compensation compensation) {
        return switch (compensation.getBasis()) {
            case MONTHLY -> compensation.getBaseAmount();
            case ANNUAL -> compensation.getBaseAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            case HOURLY -> compensation.getBaseAmount().multiply(BigDecimal.valueOf(160)); // Assuming 160 hours/month
            default -> compensation.getBaseAmount();
        };
    }

    private BigDecimal calculateAverageAnnualGrowth(List<Compensation> compensations) {
        if (compensations.size() < 2) {
            return BigDecimal.ZERO;
        }

        Compensation first = compensations.get(0);
        Compensation last = compensations.get(compensations.size() - 1);

        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(first.getEffectiveFrom(), last.getEffectiveFrom());
        double years = daysBetween / 365.25;

        if (years < 0.5 || first.getBaseAmount().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        double growthRate = Math.pow(last.getBaseAmount().doubleValue() / first.getBaseAmount().doubleValue(), 1.0 / years) - 1;

        return BigDecimal.valueOf(growthRate * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private CompensationResponse toResponse(Compensation compensation) {
        Employee employee = compensation.getEmployee();
        Currency currency = compensation.getCurrency();

        boolean isActive = compensation.getEffectiveTo() == null || !compensation.getEffectiveTo().isBefore(LocalDate.now());

        return new CompensationResponse(
            compensation.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            currency.getId(),
            currency.getCode().getCode(),
            compensation.getBaseAmount(),
            compensation.getBasis(),
            compensation.getEffectiveFrom(),
            compensation.getEffectiveTo(),
            isActive
        );
    }
}
