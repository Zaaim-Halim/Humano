package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.DeductionType;
import com.humano.domain.payroll.Currency;
import com.humano.domain.payroll.Deduction;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.CreateDeductionRequest;
import com.humano.dto.payroll.request.DeductionSearchRequest;
import com.humano.dto.payroll.response.DeductionResponse;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.DeductionRepository;
import com.humano.repository.payroll.specification.DeductionSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing employee deductions including creation, termination,
 * and calculation of deduction amounts.
 */
@Service
@Transactional
public class DeductionService {

    private static final Logger log = LoggerFactory.getLogger(DeductionService.class);

    private final DeductionRepository deductionRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;

    public DeductionService(
        DeductionRepository deductionRepository,
        EmployeeRepository employeeRepository,
        CurrencyRepository currencyRepository
    ) {
        this.deductionRepository = deductionRepository;
        this.employeeRepository = employeeRepository;
        this.currencyRepository = currencyRepository;
    }

    /**
     * Creates a new deduction for an employee.
     */
    public DeductionResponse createDeduction(CreateDeductionRequest request) {
        log.debug("Creating deduction for employee: {}", request.employeeId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee", request.employeeId()));

        Currency currency = null;
        if (request.currencyId() != null) {
            currency = currencyRepository
                .findById(request.currencyId())
                .orElseThrow(() -> new EntityNotFoundException("Currency", request.currencyId()));
        }

        // Validate deduction constraints
        validateDeduction(request);

        Deduction deduction = new Deduction();
        deduction.setEmployee(employee);
        deduction.setType(request.type());
        deduction.setAmount(request.amount());
        deduction.setPercentage(request.percentage());
        deduction.setCurrency(currency);
        deduction.setEffectiveFrom(request.effectiveFrom());
        deduction.setEffectiveTo(request.effectiveTo());
        deduction.setDescription(request.description());

        deduction = deductionRepository.save(deduction);
        log.info("Created {} deduction for employee {}", request.type(), employee.getId());

        return toResponse(deduction);
    }

    /**
     * Terminates a deduction by setting its effective end date.
     */
    public DeductionResponse terminateDeduction(UUID deductionId, LocalDate terminationDate) {
        Deduction deduction = deductionRepository
            .findById(deductionId)
            .orElseThrow(() -> new EntityNotFoundException("Deduction", deductionId));

        if (deduction.getEffectiveTo() != null && deduction.getEffectiveTo().isBefore(LocalDate.now())) {
            throw new BusinessRuleViolationException("Deduction is already terminated");
        }

        LocalDate effectiveTermination = terminationDate != null ? terminationDate : LocalDate.now();
        if (effectiveTermination.isBefore(deduction.getEffectiveFrom())) {
            throw new BusinessRuleViolationException("Termination date cannot be before effective start date");
        }

        deduction.setEffectiveTo(effectiveTermination);
        deduction = deductionRepository.save(deduction);

        log.info("Terminated deduction {} effective {}", deductionId, effectiveTermination);
        return toResponse(deduction);
    }

    /**
     * Gets all active deductions for an employee on a specific date.
     */
    @Transactional(readOnly = true)
    public List<DeductionResponse> getActiveDeductions(UUID employeeId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        List<Deduction> deductions = deductionRepository.findAll(
            (Specification<Deduction>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveDate),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), effectiveDate))
                )
        );

        return deductions.stream().map(this::toResponse).toList();
    }

    /**
     * Calculates total deduction amount for an employee based on gross pay.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calculateDeductions(UUID employeeId, BigDecimal grossPay, LocalDate asOfDate) {
        List<Deduction> activeDeductions = deductionRepository.findAll(
            (Specification<Deduction>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate))
                )
        );

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal totalDeductions = BigDecimal.ZERO;

        for (Deduction deduction : activeDeductions) {
            BigDecimal amount;
            if (deduction.getAmount() != null) {
                amount = deduction.getAmount();
            } else if (deduction.getPercentage() != null) {
                amount = grossPay.multiply(deduction.getPercentage()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            } else {
                continue;
            }

            String key = deduction.getType().name();
            result.merge(key, amount, BigDecimal::add);
            totalDeductions = totalDeductions.add(amount);
        }

        result.put("TOTAL", totalDeductions);
        return result;
    }

    /**
     * Gets all deductions by type across the organization.
     */
    @Transactional(readOnly = true)
    public Page<DeductionResponse> getDeductionsByType(DeductionType type, Pageable pageable) {
        return deductionRepository
            .findAll(
                (Specification<Deduction>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("type"), type),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), LocalDate.now()))
                    ),
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Updates a deduction's amount or percentage.
     */
    public DeductionResponse updateDeductionAmount(UUID deductionId, BigDecimal newAmount, BigDecimal newPercentage) {
        Deduction deduction = deductionRepository
            .findById(deductionId)
            .orElseThrow(() -> new EntityNotFoundException("Deduction", deductionId));

        if (newAmount != null && newPercentage != null) {
            throw new BusinessRuleViolationException("Cannot set both amount and percentage");
        }

        if (newAmount != null) {
            deduction.setAmount(newAmount);
            deduction.setPercentage(null);
        } else if (newPercentage != null) {
            deduction.setPercentage(newPercentage);
            deduction.setAmount(null);
        }

        deduction = deductionRepository.save(deduction);
        log.info("Updated deduction {} amount/percentage", deductionId);

        return toResponse(deduction);
    }

    /**
     * Gets deduction summary statistics for reporting.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDeductionStatistics(UUID departmentId, LocalDate asOfDate) {
        Specification<Deduction> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate));
            predicates.add(cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate)));
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("employee").get("department").get("id"), departmentId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<Deduction> deductions = deductionRepository.findAll(spec);

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalActiveDeductions", deductions.size());
        statistics.put(
            "byType",
            deductions
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(d -> d.getType().name(), java.util.stream.Collectors.counting()))
        );

        // Calculate total fixed amounts
        BigDecimal totalFixedAmount = deductions
            .stream()
            .filter(d -> d.getAmount() != null)
            .map(Deduction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        statistics.put("totalFixedAmount", totalFixedAmount);

        // Count percentage-based vs fixed
        long percentageBased = deductions.stream().filter(d -> d.getPercentage() != null).count();
        long fixedBased = deductions.stream().filter(d -> d.getAmount() != null).count();
        statistics.put("percentageBasedCount", percentageBased);
        statistics.put("fixedBasedCount", fixedBased);

        return statistics;
    }

    /**
     * Search deductions using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of deduction responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<DeductionResponse> searchDeductions(DeductionSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Deductions with criteria: {}", searchRequest);

        Specification<Deduction> specification = DeductionSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.type(),
            searchRequest.currencyId(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.minPercentage(),
            searchRequest.maxPercentage(),
            searchRequest.effectiveFrom(),
            searchRequest.effectiveTo(),
            searchRequest.isPreTax(),
            searchRequest.activeOnly(),
            searchRequest.description(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return deductionRepository.findAll(specification, pageable).map(this::toResponse);
    }

    /**
     * Search deductions for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID to filter by
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of deduction responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<DeductionResponse> searchDeductionsByEmployee(UUID employeeId, DeductionSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Deductions for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        Specification<Deduction> specification = DeductionSpecification.withCriteria(
            employeeId,
            searchRequest.type(),
            searchRequest.currencyId(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.minPercentage(),
            searchRequest.maxPercentage(),
            searchRequest.effectiveFrom(),
            searchRequest.effectiveTo(),
            searchRequest.isPreTax(),
            searchRequest.activeOnly(),
            searchRequest.description(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return deductionRepository.findAll(specification, pageable).map(this::toResponse);
    }

    private void validateDeduction(CreateDeductionRequest request) {
        // Validate that either amount or percentage is provided, but not both
        if (request.amount() != null && request.percentage() != null) {
            throw new BusinessRuleViolationException("Cannot specify both fixed amount and percentage for a deduction");
        }

        // Validate percentage range
        if (
            request.percentage() != null &&
            (request.percentage().compareTo(BigDecimal.ZERO) < 0 || request.percentage().compareTo(BigDecimal.valueOf(100)) > 0)
        ) {
            throw new BusinessRuleViolationException("Percentage must be between 0 and 100");
        }

        // Validate date range
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new BusinessRuleViolationException("Effective end date cannot be before effective start date");
        }
    }

    private DeductionResponse toResponse(Deduction deduction) {
        Employee employee = deduction.getEmployee();
        boolean isActive = deduction.getEffectiveTo() == null || !deduction.getEffectiveTo().isBefore(LocalDate.now());

        return new DeductionResponse(
            deduction.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            deduction.getType(),
            deduction.getAmount(),
            deduction.getPercentage(),
            deduction.getCurrency() != null ? deduction.getCurrency().getCode().getCode() : null,
            deduction.getEffectiveFrom(),
            deduction.getEffectiveTo(),
            isActive,
            deduction.getDescription()
        );
    }
}
