package com.humano.service.payroll;

import com.humano.domain.enumeration.payroll.BonusType;
import com.humano.domain.payroll.Bonus;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.Employee;
import com.humano.dto.payroll.request.AwardBonusRequest;
import com.humano.dto.payroll.request.BonusSearchRequest;
import com.humano.dto.payroll.request.BulkBonusRequest;
import com.humano.dto.payroll.response.BonusResponse;
import com.humano.dto.payroll.response.BonusSummaryResponse;
import com.humano.repository.payroll.BonusRepository;
import com.humano.repository.payroll.CurrencyRepository;
import com.humano.repository.payroll.specification.BonusSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.BusinessRuleViolationException;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Service for managing employee bonuses including awarding, approval workflows,
 * bulk operations, and analytics.
 */
@Service
@Transactional
public class BonusService {

    private static final Logger log = LoggerFactory.getLogger(BonusService.class);

    private final BonusRepository bonusRepository;
    private final EmployeeRepository employeeRepository;
    private final CurrencyRepository currencyRepository;

    public BonusService(BonusRepository bonusRepository, EmployeeRepository employeeRepository, CurrencyRepository currencyRepository) {
        this.bonusRepository = bonusRepository;
        this.employeeRepository = employeeRepository;
        this.currencyRepository = currencyRepository;
    }

    /**
     * Awards a bonus to an employee.
     */
    public BonusResponse awardBonus(AwardBonusRequest request) {
        log.debug("Awarding bonus to employee: {}", request.employeeId());

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> new EntityNotFoundException("Employee", request.employeeId()));

        Currency currency = currencyRepository
            .findById(request.currencyId())
            .orElseThrow(() -> new EntityNotFoundException("Currency", request.currencyId()));

        validateBonusRules(employee, request.type(), request.amount());

        Bonus bonus = new Bonus();
        bonus.setEmployee(employee);
        bonus.setType(request.type());
        bonus.setAmount(request.amount());
        bonus.setCurrency(currency);
        bonus.setAwardDate(request.awardDate());
        bonus.setPaymentDate(request.payImmediately() ? request.awardDate() : request.paymentDate());
        bonus.setIsPaid(request.payImmediately());
        bonus.setDescription(request.description());

        bonus = bonusRepository.save(bonus);
        log.info("Awarded {} bonus of {} to employee {}", request.type(), request.amount(), employee.getId());

        return toResponse(bonus);
    }

    /**
     * Awards bonuses to multiple employees in bulk.
     */
    public List<BonusResponse> awardBulkBonuses(BulkBonusRequest request) {
        log.debug("Awarding bulk bonuses to {} employees", request.employeeIds().size());

        Currency currency = currencyRepository
            .findById(request.currencyId())
            .orElseThrow(() -> new EntityNotFoundException("Currency", request.currencyId()));

        List<Employee> employees = employeeRepository.findAllById(request.employeeIds());
        if (employees.size() != request.employeeIds().size()) {
            Set<UUID> foundIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
            List<UUID> missingIds = request.employeeIds().stream().filter(id -> !foundIds.contains(id)).toList();
            throw new EntityNotFoundException("Employees not found: " + missingIds);
        }

        Map<UUID, BigDecimal> amountByEmployee = new HashMap<>();
        if (request.individualAmounts() != null && !request.individualAmounts().isEmpty()) {
            for (BulkBonusRequest.EmployeeBonusAmount item : request.individualAmounts()) {
                amountByEmployee.put(item.employeeId(), item.amount());
            }
        }

        List<Bonus> bonuses = new ArrayList<>();
        for (Employee employee : employees) {
            BigDecimal amount = amountByEmployee.getOrDefault(employee.getId(), request.uniformAmount());
            if (amount == null) {
                throw new BusinessRuleViolationException("No amount specified for employee " + employee.getId());
            }

            Bonus bonus = new Bonus();
            bonus.setEmployee(employee);
            bonus.setType(request.type());
            bonus.setAmount(amount);
            bonus.setCurrency(currency);
            bonus.setAwardDate(request.awardDate());
            bonus.setPaymentDate(request.paymentDate());
            bonus.setIsPaid(false);
            bonus.setDescription(request.description());
            bonuses.add(bonus);
        }

        List<Bonus> savedBonuses = bonusRepository.saveAll(bonuses);
        log.info("Awarded {} bulk bonuses of type {}", savedBonuses.size(), request.type());

        return savedBonuses.stream().map(this::toResponse).toList();
    }

    /**
     * Marks a bonus as paid.
     */
    public BonusResponse markAsPaid(UUID bonusId, LocalDate paymentDate) {
        Bonus bonus = bonusRepository.findById(bonusId).orElseThrow(() -> new EntityNotFoundException("Bonus", bonusId));

        if (Boolean.TRUE.equals(bonus.getIsPaid())) {
            throw new BusinessRuleViolationException("Bonus is already marked as paid");
        }

        bonus.setIsPaid(true);
        bonus.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        bonus = bonusRepository.save(bonus);

        log.info("Marked bonus {} as paid on {}", bonusId, bonus.getPaymentDate());
        return toResponse(bonus);
    }

    /**
     * Marks multiple bonuses as paid in bulk.
     */
    public List<BonusResponse> markBulkAsPaid(List<UUID> bonusIds, LocalDate paymentDate) {
        List<Bonus> bonuses = bonusRepository.findAllById(bonusIds);
        LocalDate effectivePaymentDate = paymentDate != null ? paymentDate : LocalDate.now();

        for (Bonus bonus : bonuses) {
            if (!Boolean.TRUE.equals(bonus.getIsPaid())) {
                bonus.setIsPaid(true);
                bonus.setPaymentDate(effectivePaymentDate);
            }
        }

        return bonusRepository.saveAll(bonuses).stream().map(this::toResponse).toList();
    }

    /**
     * Retrieves a comprehensive bonus summary for an employee.
     */
    @Transactional(readOnly = true)
    public BonusSummaryResponse getBonusSummary(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));

        List<Bonus> allBonuses = bonusRepository.findAll(
            (Specification<Bonus>) (root, query, cb) -> {
                if (query != null) {
                    query.orderBy(cb.desc(root.get("awardDate")));
                }
                return cb.equal(root.get("employee").get("id"), employeeId);
            }
        );

        BigDecimal totalAmount = allBonuses.stream().map(Bonus::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidAmount = allBonuses
            .stream()
            .filter(b -> Boolean.TRUE.equals(b.getIsPaid()))
            .map(Bonus::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = allBonuses
            .stream()
            .filter(b -> !Boolean.TRUE.equals(b.getIsPaid()))
            .map(Bonus::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> bonusByType = allBonuses
            .stream()
            .collect(
                Collectors.groupingBy(b -> b.getType().name(), Collectors.reducing(BigDecimal.ZERO, Bonus::getAmount, BigDecimal::add))
            );

        // Year-to-date calculations
        LocalDate startOfYear = LocalDate.now().withDayOfYear(1);
        List<Bonus> ytdBonuses = allBonuses.stream().filter(b -> !b.getAwardDate().isBefore(startOfYear)).toList();

        BigDecimal ytdAmount = ytdBonuses.stream().map(Bonus::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BonusResponse> recentBonuses = allBonuses.stream().limit(5).map(this::toResponse).toList();

        return new BonusSummaryResponse(
            employeeId,
            employee.getFirstName() + " " + employee.getLastName(),
            allBonuses.size(),
            totalAmount,
            paidAmount,
            pendingAmount,
            bonusByType,
            recentBonuses,
            ytdBonuses.size(),
            ytdAmount
        );
    }

    /**
     * Gets all pending (unpaid) bonuses for payment processing.
     */
    @Transactional(readOnly = true)
    public Page<BonusResponse> getPendingBonuses(LocalDate paymentDateBefore, Pageable pageable) {
        return bonusRepository
            .findAll(
                (Specification<Bonus>) (root, query, cb) -> {
                    List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.isFalse(root.get("isPaid")));
                    if (paymentDateBefore != null) {
                        predicates.add(cb.lessThanOrEqualTo(root.get("paymentDate"), paymentDateBefore));
                    }
                    return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
                },
                pageable
            )
            .map(this::toResponse);
    }

    /**
     * Gets bonus analytics for a department.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDepartmentBonusAnalytics(UUID departmentId, int year) {
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        List<Bonus> bonuses = bonusRepository.findAll(
            (Specification<Bonus>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("department").get("id"), departmentId),
                    cb.between(root.get("awardDate"), startDate, endDate)
                )
        );

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("year", year);
        analytics.put("totalBonuses", bonuses.size());
        analytics.put("totalAmount", bonuses.stream().map(Bonus::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        analytics.put(
            "averageAmount",
            bonuses.isEmpty()
                ? BigDecimal.ZERO
                : ((BigDecimal) analytics.get("totalAmount")).divide(BigDecimal.valueOf(bonuses.size()), 2, RoundingMode.HALF_UP)
        );

        // By type breakdown
        analytics.put(
            "byType",
            bonuses
                .stream()
                .collect(
                    Collectors.groupingBy(
                        b -> b.getType().name(),
                        Collectors.collectingAndThen(Collectors.toList(), list ->
                            Map.of(
                                "count",
                                list.size(),
                                "total",
                                list.stream().map(Bonus::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                            )
                        )
                    )
                )
        );

        // By month breakdown
        analytics.put(
            "byMonth",
            bonuses
                .stream()
                .collect(
                    Collectors.groupingBy(
                        b -> b.getAwardDate().getMonth().name(),
                        Collectors.reducing(BigDecimal.ZERO, Bonus::getAmount, BigDecimal::add)
                    )
                )
        );

        return analytics;
    }

    /**
     * Search bonuses using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of bonus responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<BonusResponse> searchBonuses(BonusSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Bonuses with criteria: {}", searchRequest);

        Specification<Bonus> specification = BonusSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.type(),
            searchRequest.currencyId(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.awardDateFrom(),
            searchRequest.awardDateTo(),
            searchRequest.paymentDateFrom(),
            searchRequest.paymentDateTo(),
            searchRequest.isPaid(),
            searchRequest.isTaxable(),
            searchRequest.description(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return bonusRepository.findAll(specification, pageable).map(this::toResponse);
    }

    /**
     * Search bonuses for a specific employee using multiple criteria with pagination.
     *
     * @param employeeId the employee ID to filter by
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of bonus responses matching the criteria
     */
    @Transactional(readOnly = true)
    public Page<BonusResponse> searchBonusesByEmployee(UUID employeeId, BonusSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search Bonuses for Employee: {} with criteria: {}", employeeId, searchRequest);

        // Override employeeId in search request to ensure it matches the path parameter
        Specification<Bonus> specification = BonusSpecification.withCriteria(
            employeeId,
            searchRequest.type(),
            searchRequest.currencyId(),
            searchRequest.minAmount(),
            searchRequest.maxAmount(),
            searchRequest.awardDateFrom(),
            searchRequest.awardDateTo(),
            searchRequest.paymentDateFrom(),
            searchRequest.paymentDateTo(),
            searchRequest.isPaid(),
            searchRequest.isTaxable(),
            searchRequest.description(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo()
        );

        return bonusRepository.findAll(specification, pageable).map(this::toResponse);
    }

    private void validateBonusRules(Employee employee, BonusType type, BigDecimal amount) {
        // Example validation rules - can be extended based on business requirements
        if (type == BonusType.SIGNING) {
            // Check if employee already received a signing bonus
            boolean hasSigningBonus = bonusRepository.exists(
                (Specification<Bonus>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("employee").get("id"), employee.getId()), cb.equal(root.get("type"), BonusType.SIGNING))
            );
            if (hasSigningBonus) {
                throw new BusinessRuleViolationException("Employee already received a signing bonus");
            }
        }

        // Check for reasonable bonus limits based on type
        BigDecimal maxAllowed = getMaxBonusForType(type);
        if (amount.compareTo(maxAllowed) > 0) {
            log.warn("Bonus amount {} exceeds typical limit {} for type {}", amount, maxAllowed, type);
        }
    }

    private BigDecimal getMaxBonusForType(BonusType type) {
        return switch (type) {
            case SPOT -> BigDecimal.valueOf(5000);
            case REFERRAL -> BigDecimal.valueOf(10000);
            case SIGNING -> BigDecimal.valueOf(50000);
            case PERFORMANCE, YEAR_END -> BigDecimal.valueOf(100000);
            default -> BigDecimal.valueOf(1000000);
        };
    }

    private BonusResponse toResponse(Bonus bonus) {
        Employee employee = bonus.getEmployee();
        return new BonusResponse(
            bonus.getId(),
            employee.getId(),
            employee.getFirstName() + " " + employee.getLastName(),
            bonus.getType(),
            bonus.getAmount(),
            bonus.getCurrency().getCode(),
            bonus.getAwardDate(),
            bonus.getPaymentDate(),
            bonus.getIsPaid(),
            bonus.getDescription()
        );
    }
}
