package com.humano.service.payroll;

import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.BenefitStatus;
import com.humano.domain.enumeration.payroll.BenefitType;
import com.humano.domain.hr.Employee;
import com.humano.domain.payroll.EmployeeBenefit;
import com.humano.dto.payroll.request.EnrollBenefitRequest;
import com.humano.dto.payroll.response.BenefitsSummaryResponse;
import com.humano.dto.payroll.response.EmployeeBenefitResponse;
import com.humano.repository.CurrencyRepository;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.payroll.EmployeeBenefitRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing employee benefits including enrollment, termination,
 * cost tracking, and benefit analytics.
 */
@Service
@Transactional
public class EmployeeBenefitService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeBenefitService.class);

    private final EmployeeBenefitRepository benefitRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;

    public EmployeeBenefitService(
        EmployeeBenefitRepository benefitRepository,
        EmployeeRepository employeeRepository,
        CurrencyRepository currencyRepository
    ) {
        this.benefitRepository = benefitRepository;
        this.employeeRepository = employeeRepository;
        this.currencyRepository = currencyRepository;
    }

    /**
     * Enrolls an employee in a benefit plan.
     */
    public EmployeeBenefitResponse enrollBenefit(EnrollBenefitRequest request) {
        log.debug("Enrolling employee {} in benefit {}", request.employeeId(), request.type());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee", request.employeeId()));

        Currency currency = null;
        if (request.currencyId() != null) {
            currency = currencyRepository
                .findById(request.currencyId())
                .orElseThrow(() -> new EntityNotFoundException("Currency", request.currencyId()));
        }

        // Check for duplicate active benefits of same type
        validateNoDuplicateActiveBenefit(employee.getId(), request.type(), request.effectiveFrom());

        EmployeeBenefit benefit = new EmployeeBenefit();
        benefit.setEmployee(employee);
        benefit.setType(request.type());
        benefit.setEmployerCost(request.employerCost() != null ? request.employerCost() : BigDecimal.ZERO);
        benefit.setEmployeeCost(request.employeeCost() != null ? request.employeeCost() : BigDecimal.ZERO);
        benefit.setCurrency(currency);
        benefit.setEffectiveFrom(request.effectiveFrom());
        benefit.setEffectiveTo(request.effectiveTo());
        benefit.setStatus(BenefitStatus.ACTIVE);

        benefit = benefitRepository.save(benefit);
        log.info("Enrolled employee {} in {} benefit", employee.getId(), request.type());

        return toResponse(benefit);
    }

    /**
     * Terminates an employee's benefit enrollment.
     */
    public EmployeeBenefitResponse terminateBenefit(UUID benefitId, LocalDate terminationDate, String reason) {
        EmployeeBenefit benefit = benefitRepository
            .findById(benefitId)
            .orElseThrow(() -> new EntityNotFoundException("EmployeeBenefit", benefitId));

        if (benefit.getStatus() == BenefitStatus.TERMINATED) {
            throw new BusinessRuleViolationException("Benefit is already terminated");
        }

        LocalDate effectiveTermination = terminationDate != null ? terminationDate : LocalDate.now();
        if (effectiveTermination.isBefore(benefit.getEffectiveFrom())) {
            throw new BusinessRuleViolationException("Termination date cannot be before enrollment date");
        }

        benefit.setEffectiveTo(effectiveTermination);
        benefit.setStatus(BenefitStatus.TERMINATED);
        benefit = benefitRepository.save(benefit);

        log.info("Terminated benefit {} for employee {} effective {}", benefitId, benefit.getEmployee().getId(), effectiveTermination);

        return toResponse(benefit);
    }

    /**
     * Updates benefit costs (e.g., for annual renewal).
     */
    public EmployeeBenefitResponse updateBenefitCosts(UUID benefitId, BigDecimal newEmployerCost, BigDecimal newEmployeeCost) {
        EmployeeBenefit benefit = benefitRepository
            .findById(benefitId)
            .orElseThrow(() -> new EntityNotFoundException("EmployeeBenefit", benefitId));

        if (benefit.getStatus() != BenefitStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Can only update costs for active benefits");
        }

        if (newEmployerCost != null) {
            benefit.setEmployerCost(newEmployerCost);
        }
        if (newEmployeeCost != null) {
            benefit.setEmployeeCost(newEmployeeCost);
        }

        benefit = benefitRepository.save(benefit);
        log.info("Updated costs for benefit {}", benefitId);

        return toResponse(benefit);
    }

    /**
     * Gets a comprehensive benefits summary for an employee.
     */
    @Transactional(readOnly = true)
    public BenefitsSummaryResponse getBenefitsSummary(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));

        LocalDate today = LocalDate.now();

        List<EmployeeBenefit> allBenefits = benefitRepository.findAll(
            (Specification<EmployeeBenefit>) (root, query, cb) -> cb.equal(root.get("employee").get("id"), employeeId)
        );

        // Active benefits
        List<EmployeeBenefit> activeBenefits = allBenefits
            .stream()
            .filter(b -> b.getStatus() == BenefitStatus.ACTIVE)
            .filter(b -> !b.getEffectiveFrom().isAfter(today))
            .filter(b -> b.getEffectiveTo() == null || !b.getEffectiveTo().isBefore(today))
            .toList();

        // Upcoming benefits (not yet effective)
        List<EmployeeBenefit> upcomingBenefits = allBenefits
            .stream()
            .filter(b -> b.getStatus() == BenefitStatus.ACTIVE || b.getStatus() == BenefitStatus.PENDING)
            .filter(b -> b.getEffectiveFrom().isAfter(today))
            .toList();

        // Calculate totals
        BigDecimal totalEmployerCost = activeBenefits
            .stream()
            .map(EmployeeBenefit::getEmployerCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEmployeeCost = activeBenefits
            .stream()
            .map(EmployeeBenefit::getEmployeeCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMonthlyCost = totalEmployerCost.add(totalEmployeeCost);
        BigDecimal annualBenefitValue = totalMonthlyCost.multiply(BigDecimal.valueOf(12));

        // Cost breakdown by type
        Map<String, BigDecimal> costByType = activeBenefits
            .stream()
            .collect(
                Collectors.groupingBy(
                    b -> b.getType().name(),
                    Collectors.reducing(BigDecimal.ZERO, b -> b.getEmployerCost().add(b.getEmployeeCost()), BigDecimal::add)
                )
            );

        return new BenefitsSummaryResponse(
            employeeId,
            employee.getFirstName() + " " + employee.getLastName(),
            activeBenefits.size(),
            totalEmployerCost,
            totalEmployeeCost,
            totalMonthlyCost,
            annualBenefitValue,
            costByType,
            activeBenefits.stream().map(this::toResponse).toList(),
            upcomingBenefits.stream().map(this::toResponse).toList()
        );
    }

    /**
     * Gets all active benefits by type across the organization.
     */
    @Transactional(readOnly = true)
    public Page<EmployeeBenefitResponse> getActiveBenefitsByType(BenefitType type, Pageable pageable) {
        LocalDate today = LocalDate.now();

        return benefitRepository
            .findAll(
                (Specification<EmployeeBenefit>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("type"), type),
                        cb.equal(root.get("status"), BenefitStatus.ACTIVE),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), today),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), today))
                    ),
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Calculates total benefit costs for a department or organization.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> calculateBenefitCosts(UUID departmentId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();

        Specification<EmployeeBenefit> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), BenefitStatus.ACTIVE));
            predicates.add(cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveDate));
            predicates.add(cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), effectiveDate)));
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("employee").get("department").get("id"), departmentId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<EmployeeBenefit> benefits = benefitRepository.findAll(spec);

        Map<String, Object> result = new HashMap<>();

        BigDecimal totalEmployerCost = benefits.stream().map(EmployeeBenefit::getEmployerCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalEmployeeCost = benefits.stream().map(EmployeeBenefit::getEmployeeCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        result.put("totalMonthlyEmployerCost", totalEmployerCost);
        result.put("totalMonthlyEmployeeCost", totalEmployeeCost);
        result.put("totalMonthlyCost", totalEmployerCost.add(totalEmployeeCost));
        result.put("annualEmployerCost", totalEmployerCost.multiply(BigDecimal.valueOf(12)));
        result.put("annualEmployeeCost", totalEmployeeCost.multiply(BigDecimal.valueOf(12)));
        result.put("enrolledEmployeeCount", benefits.stream().map(b -> b.getEmployee().getId()).distinct().count());
        result.put("totalEnrollments", benefits.size());

        // Breakdown by type
        result.put(
            "costByType",
            benefits
                .stream()
                .collect(
                    Collectors.groupingBy(
                        b -> b.getType().name(),
                        Collectors.collectingAndThen(Collectors.toList(), list ->
                            Map.of(
                                "count",
                                list.size(),
                                "employerCost",
                                list.stream().map(EmployeeBenefit::getEmployerCost).reduce(BigDecimal.ZERO, BigDecimal::add),
                                "employeeCost",
                                list.stream().map(EmployeeBenefit::getEmployeeCost).reduce(BigDecimal.ZERO, BigDecimal::add)
                            )
                        )
                    )
                )
        );

        return result;
    }

    /**
     * Bulk enrolls employees in a benefit plan.
     */
    public List<EmployeeBenefitResponse> bulkEnrollBenefits(
        List<UUID> employeeIds,
        BenefitType type,
        BigDecimal employerCost,
        BigDecimal employeeCost,
        UUID currencyId,
        LocalDate effectiveFrom
    ) {
        log.info("Bulk enrolling {} employees in {} benefit", employeeIds.size(), type);

        Currency currency = null;
        if (currencyId != null) {
            currency = currencyRepository.findById(currencyId).orElseThrow(() -> new EntityNotFoundException("Currency", currencyId));
        }

        List<Employee> employees = employeeRepository.findAllById(employeeIds);
        List<EmployeeBenefit> benefits = new ArrayList<>();

        for (Employee employee : employees) {
            try {
                validateNoDuplicateActiveBenefit(employee.getId(), type, effectiveFrom);

                EmployeeBenefit benefit = new EmployeeBenefit();
                benefit.setEmployee(employee);
                benefit.setType(type);
                benefit.setEmployerCost(employerCost != null ? employerCost : BigDecimal.ZERO);
                benefit.setEmployeeCost(employeeCost != null ? employeeCost : BigDecimal.ZERO);
                benefit.setCurrency(currency);
                benefit.setEffectiveFrom(effectiveFrom);
                benefit.setStatus(BenefitStatus.ACTIVE);
                benefits.add(benefit);
            } catch (BusinessRuleViolationException e) {
                log.warn("Skipping employee {} for bulk enrollment: {}", employee.getId(), e.getMessage());
            }
        }

        List<EmployeeBenefit> savedBenefits = benefitRepository.saveAll(benefits);
        log.info("Enrolled {} employees in {} benefit", savedBenefits.size(), type);

        return savedBenefits.stream().map(this::toResponse).toList();
    }

    /**
     * Gets benefits expiring within a specified period for renewal notifications.
     */
    @Transactional(readOnly = true)
    public List<EmployeeBenefitResponse> getBenefitsExpiringSoon(int daysAhead) {
        LocalDate today = LocalDate.now();
        LocalDate expirationThreshold = today.plusDays(daysAhead);

        return benefitRepository
            .findAll(
                (Specification<EmployeeBenefit>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("status"), BenefitStatus.ACTIVE),
                        cb.isNotNull(root.get("effectiveTo")),
                        cb.between(root.get("effectiveTo"), today, expirationThreshold)
                    )
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private void validateNoDuplicateActiveBenefit(UUID employeeId, BenefitType type, LocalDate effectiveFrom) {
        boolean hasActiveBenefit = benefitRepository.exists(
            (Specification<EmployeeBenefit>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.equal(root.get("type"), type),
                    cb.equal(root.get("status"), BenefitStatus.ACTIVE),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), effectiveFrom),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), effectiveFrom))
                )
        );

        if (hasActiveBenefit) {
            throw new BusinessRuleViolationException("Employee already has an active " + type + " benefit");
        }
    }

    private EmployeeBenefitResponse toResponse(EmployeeBenefit benefit) {
        Employee employee = benefit.getEmployee();
        BigDecimal totalCost = benefit.getEmployerCost().add(benefit.getEmployeeCost());

        return new EmployeeBenefitResponse(
            benefit.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            benefit.getType(),
            benefit.getEmployerCost(),
            benefit.getEmployeeCost(),
            totalCost,
            benefit.getCurrency() != null ? benefit.getCurrency().getCode() : null,
            benefit.getEffectiveFrom(),
            benefit.getEffectiveTo(),
            benefit.getStatus(),
            null, // planName - would come from additional field
            null // coverageLevel - would come from additional field
        );
    }
}
