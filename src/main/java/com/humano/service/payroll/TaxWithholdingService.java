package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.TaxType;
import com.humano.domain.payroll.TaxWithholding;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.CreateTaxWithholdingRequest;
import com.humano.dto.payroll.response.TaxWithholdingResponse;
import com.humano.repository.payroll.TaxWithholdingRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Service for managing employee tax withholdings including creation, updates,
 * and year-to-date tracking.
 */
@Service
@Transactional
public class TaxWithholdingService {

    private static final Logger log = LoggerFactory.getLogger(TaxWithholdingService.class);

    private final TaxWithholdingRepository withholdingRepository;
    private final EmployeeRepository employeeRepository;

    public TaxWithholdingService(TaxWithholdingRepository withholdingRepository, EmployeeRepository employeeRepository) {
        this.withholdingRepository = withholdingRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Creates a new tax withholding configuration for an employee.
     */
    public TaxWithholdingResponse createWithholding(CreateTaxWithholdingRequest request) {
        log.debug("Creating {} tax withholding for employee {}", request.type(), request.employeeId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee", request.employeeId()));

        // Close any overlapping withholdings of the same type
        closeOverlappingWithholdings(employee.getId(), request.type(), request.effectiveFrom());

        TaxWithholding withholding = new TaxWithholding();
        withholding.setEmployee(employee);
        withholding.setType(request.type());
        withholding.setRate(request.rate());
        withholding.setEffectiveFrom(request.effectiveFrom());
        withholding.setEffectiveTo(request.effectiveTo());
        withholding.setTaxAuthority(request.taxAuthority());
        withholding.setTaxIdentifier(request.taxIdentifier());
        withholding.setYearToDateAmount(BigDecimal.ZERO);

        withholding = withholdingRepository.save(withholding);
        log.info("Created {} tax withholding for employee {}", request.type(), employee.getId());

        return toResponse(withholding);
    }

    /**
     * Updates the tax rate for a withholding.
     */
    public TaxWithholdingResponse updateRate(UUID withholdingId, BigDecimal newRate) {
        TaxWithholding withholding = withholdingRepository
            .findById(withholdingId)
            .orElseThrow(() -> new EntityNotFoundException("TaxWithholding", withholdingId));

        if (newRate.compareTo(BigDecimal.ZERO) < 0 || newRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessRuleViolationException("Tax rate must be between 0 and 100");
        }

        withholding.setRate(newRate);
        withholding = withholdingRepository.save(withholding);

        log.info("Updated tax rate for withholding {} to {}%", withholdingId, newRate);
        return toResponse(withholding);
    }

    /**
     * Terminates a tax withholding.
     */
    public TaxWithholdingResponse terminateWithholding(UUID withholdingId, LocalDate terminationDate) {
        TaxWithholding withholding = withholdingRepository
            .findById(withholdingId)
            .orElseThrow(() -> new EntityNotFoundException("TaxWithholding", withholdingId));

        LocalDate effectiveTermination = terminationDate != null ? terminationDate : LocalDate.now();
        if (effectiveTermination.isBefore(withholding.getEffectiveFrom())) {
            throw new BusinessRuleViolationException("Termination date cannot be before effective start date");
        }

        withholding.setEffectiveTo(effectiveTermination);
        withholding = withholdingRepository.save(withholding);

        log.info("Terminated tax withholding {} effective {}", withholdingId, effectiveTermination);
        return toResponse(withholding);
    }

    /**
     * Gets all active tax withholdings for an employee.
     */
    @Transactional(readOnly = true)
    public List<TaxWithholdingResponse> getActiveWithholdings(UUID employeeId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        return withholdingRepository
            .findAll(
                (Specification<TaxWithholding>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveDate),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), effectiveDate))
                    )
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Calculates and records tax withheld for a pay period.
     */
    public TaxWithholdingResponse recordWithholding(UUID withholdingId, BigDecimal grossPay, BigDecimal withheldAmount) {
        TaxWithholding withholding = withholdingRepository
            .findById(withholdingId)
            .orElseThrow(() -> new EntityNotFoundException("TaxWithholding", withholdingId));

        // Update year-to-date amount
        BigDecimal newYtd = withholding.getYearToDateAmount().add(withheldAmount);
        withholding.setYearToDateAmount(newYtd);
        withholding = withholdingRepository.save(withholding);

        log.debug("Recorded {} tax withheld for withholding {}, YTD: {}", withheldAmount, withholdingId, newYtd);

        return toResponse(withholding);
    }

    /**
     * Resets year-to-date amounts for a new tax year.
     */
    public int resetYearToDateAmounts(int year) {
        log.info("Resetting YTD amounts for tax year {}", year);

        List<TaxWithholding> allWithholdings = withholdingRepository.findAll();
        int resetCount = 0;

        for (TaxWithholding withholding : allWithholdings) {
            if (withholding.getYearToDateAmount().compareTo(BigDecimal.ZERO) > 0) {
                // Could archive the YTD amount before resetting
                withholding.setYearToDateAmount(BigDecimal.ZERO);
                withholdingRepository.save(withholding);
                resetCount++;
            }
        }

        log.info("Reset YTD amounts for {} withholdings", resetCount);
        return resetCount;
    }

    /**
     * Gets withholdings by tax type across the organization.
     */
    @Transactional(readOnly = true)
    public Page<TaxWithholdingResponse> getWithholdingsByType(TaxType type, Pageable pageable) {
        LocalDate today = LocalDate.now();

        return withholdingRepository
            .findAll(
                (Specification<TaxWithholding>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("type"), type),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), today),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), today))
                    ),
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Calculates total tax liability for an employee.
     */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> calculateTaxLiability(UUID employeeId, BigDecimal grossPay, LocalDate asOfDate) {
        List<TaxWithholding> withholdings = withholdingRepository.findAll(
            (Specification<TaxWithholding>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), asOfDate),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), asOfDate))
                )
        );

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        BigDecimal totalTax = BigDecimal.ZERO;

        for (TaxWithholding withholding : withholdings) {
            BigDecimal taxAmount = grossPay.multiply(withholding.getRate()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            result.put(withholding.getType().name(), taxAmount);
            totalTax = totalTax.add(taxAmount);
        }

        result.put("TOTAL", totalTax);
        return result;
    }

    /**
     * Gets tax withholding statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getWithholdingStatistics(UUID departmentId) {
        LocalDate today = LocalDate.now();

        Specification<TaxWithholding> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), today));
            predicates.add(cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), today)));
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("employee").get("department").get("id"), departmentId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<TaxWithholding> withholdings = withholdingRepository.findAll(spec);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActiveWithholdings", withholdings.size());
        stats.put("uniqueEmployees", withholdings.stream().map(w -> w.getEmployee().getId()).distinct().count());

        // By type
        stats.put(
            "byType",
            withholdings
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(w -> w.getType().name(), java.util.stream.Collectors.counting()))
        );

        // Average rates by type
        stats.put(
            "avgRateByType",
            withholdings
                .stream()
                .collect(
                    java.util.stream.Collectors.groupingBy(
                        w -> w.getType().name(),
                        java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), list ->
                            list
                                .stream()
                                .map(TaxWithholding::getRate)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(list.size()), 2, RoundingMode.HALF_UP)
                        )
                    )
                )
        );

        // Total YTD withheld
        BigDecimal totalYtd = withholdings.stream().map(TaxWithholding::getYearToDateAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalYearToDate", totalYtd);

        return stats;
    }

    private void closeOverlappingWithholdings(UUID employeeId, TaxType type, LocalDate newEffectiveFrom) {
        List<TaxWithholding> overlapping = withholdingRepository.findAll(
            (Specification<TaxWithholding>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.equal(root.get("type"), type),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), newEffectiveFrom),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), newEffectiveFrom))
                )
        );

        for (TaxWithholding withholding : overlapping) {
            withholding.setEffectiveTo(newEffectiveFrom.minusDays(1));
            withholdingRepository.save(withholding);
        }
    }

    private TaxWithholdingResponse toResponse(TaxWithholding withholding) {
        Employee employee = withholding.getEmployee();
        boolean isActive = withholding.getEffectiveTo() == null || !withholding.getEffectiveTo().isBefore(LocalDate.now());

        return new TaxWithholdingResponse(
            withholding.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            withholding.getType(),
            withholding.getRate(),
            withholding.getEffectiveFrom(),
            withholding.getEffectiveTo(),
            withholding.getTaxAuthority(),
            withholding.getTaxIdentifier(),
            withholding.getYearToDateAmount(),
            isActive
        );
    }
}
