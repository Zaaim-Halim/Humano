package com.humano.service.payroll;

import com.humano.domain.hr.Employee;
import com.humano.domain.payroll.*;
import com.humano.dto.payroll.response.TotalCompensationResponse;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.payroll.*;
import com.humano.service.errors.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating comprehensive total compensation statements
 * that include all forms of compensation: salary, bonuses, benefits, etc.
 */
@Service
@Transactional(readOnly = true)
public class TotalCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TotalCompensationService.class);

    private final EmployeeRepository employeeRepository;
    private final CompensationRepository compensationRepository;
    private final BonusRepository bonusRepository;
    private final EmployeeBenefitRepository benefitRepository;
    private final PayrollResultRepository resultRepository;

    public TotalCompensationService(
        EmployeeRepository employeeRepository,
        CompensationRepository compensationRepository,
        BonusRepository bonusRepository,
        EmployeeBenefitRepository benefitRepository,
        PayrollResultRepository resultRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.compensationRepository = compensationRepository;
        this.bonusRepository = bonusRepository;
        this.benefitRepository = benefitRepository;
        this.resultRepository = resultRepository;
    }

    /**
     * Generates a total compensation statement for an employee for a given year.
     */
    public TotalCompensationResponse generateCompensationStatement(UUID employeeId, int year) {
        log.debug("Generating total compensation statement for employee {} for year {}", employeeId, year);

        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new EntityNotFoundException("Employee", employeeId));

        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        // Get base salary (current compensation annualized)
        BigDecimal baseSalary = calculateAnnualBaseSalary(employeeId, year);

        // Get total bonuses
        BigDecimal totalBonuses = calculateTotalBonuses(employeeId, yearStart, yearEnd);
        Map<String, BigDecimal> bonusByType = getBonusByType(employeeId, yearStart, yearEnd);

        // Get benefits value
        BigDecimal totalBenefitsValue = calculateBenefitsValue(employeeId, yearStart, yearEnd);
        Map<String, BigDecimal> benefitsByType = getBenefitsByType(employeeId, yearStart, yearEnd);

        // Calculate total compensation
        BigDecimal totalCompensation = baseSalary.add(totalBonuses).add(totalBenefitsValue);

        // Get monthly details from payroll results
        List<TotalCompensationResponse.MonthlyCompensation> monthlyDetails = getMonthlyCompensation(employeeId, year);

        // Calculate breakdown percentages
        TotalCompensationResponse.CompensationBreakdown breakdown = calculateBreakdown(
            baseSalary,
            totalBonuses,
            totalBenefitsValue,
            bonusByType,
            benefitsByType
        );

        // Get year-over-year comparison
        TotalCompensationResponse.YearOverYearComparison comparison = calculateYearComparison(employeeId, year);

        // Get currency (from current compensation)
        String currencyCode = getCurrencyCode(employeeId);

        return new TotalCompensationResponse(
            employeeId,
            employee.getFirstName() + " " + employee.getLastName(),
            employee.getPosition() != null ? employee.getPosition().getName() : null,
            employee.getDepartment() != null ? employee.getDepartment().getName() : null,
            year,
            baseSalary,
            totalBonuses,
            totalBenefitsValue,
            totalCompensation,
            currencyCode,
            breakdown,
            monthlyDetails,
            comparison
        );
    }

    /**
     * Compares compensation between two employees.
     */
    public Map<String, Object> compareCompensation(UUID employeeId1, UUID employeeId2, int year) {
        TotalCompensationResponse emp1 = generateCompensationStatement(employeeId1, year);
        TotalCompensationResponse emp2 = generateCompensationStatement(employeeId2, year);

        Map<String, Object> comparison = new HashMap<>();
        comparison.put("year", year);
        comparison.put("employee1", Map.of("id", employeeId1, "name", emp1.employeeName(), "total", emp1.totalCompensation()));
        comparison.put("employee2", Map.of("id", employeeId2, "name", emp2.employeeName(), "total", emp2.totalCompensation()));

        BigDecimal difference = emp1.totalCompensation().subtract(emp2.totalCompensation());
        BigDecimal percentDifference = emp2.totalCompensation().compareTo(BigDecimal.ZERO) > 0
            ? difference.divide(emp2.totalCompensation(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        comparison.put("difference", difference);
        comparison.put("percentDifference", percentDifference);

        // Component-level comparison
        comparison.put("baseSalaryDiff", emp1.baseSalary().subtract(emp2.baseSalary()));
        comparison.put("bonusesDiff", emp1.totalBonuses().subtract(emp2.totalBonuses()));
        comparison.put("benefitsDiff", emp1.totalBenefitsValue().subtract(emp2.totalBenefitsValue()));

        return comparison;
    }

    /**
     * Gets department compensation summary.
     */
    public Map<String, Object> getDepartmentCompensationSummary(UUID departmentId, int year) {
        List<Employee> employees = employeeRepository.findAll(
            (Specification<Employee>) (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId)
        );

        List<TotalCompensationResponse> statements = employees.stream().map(e -> generateCompensationStatement(e.getId(), year)).toList();

        BigDecimal totalBaseSalary = statements
            .stream()
            .map(TotalCompensationResponse::baseSalary)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBonuses = statements.stream().map(TotalCompensationResponse::totalBonuses).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalBenefits = statements
            .stream()
            .map(TotalCompensationResponse::totalBenefitsValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCompensation = statements
            .stream()
            .map(TotalCompensationResponse::totalCompensation)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgCompensation = employees.isEmpty()
            ? BigDecimal.ZERO
            : totalCompensation.divide(BigDecimal.valueOf(employees.size()), 2, RoundingMode.HALF_UP);

        // Find min and max
        BigDecimal minCompensation = statements
            .stream()
            .map(TotalCompensationResponse::totalCompensation)
            .min(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);

        BigDecimal maxCompensation = statements
            .stream()
            .map(TotalCompensationResponse::totalCompensation)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);

        Map<String, Object> summary = new HashMap<>();
        summary.put("year", year);
        summary.put("employeeCount", employees.size());
        summary.put("totalBaseSalary", totalBaseSalary);
        summary.put("totalBonuses", totalBonuses);
        summary.put("totalBenefits", totalBenefits);
        summary.put("totalCompensation", totalCompensation);
        summary.put("avgCompensation", avgCompensation);
        summary.put("minCompensation", minCompensation);
        summary.put("maxCompensation", maxCompensation);
        summary.put("compensationRange", maxCompensation.subtract(minCompensation));

        return summary;
    }

    /**
     * Gets compensation percentile for an employee.
     */
    public Map<String, Object> getCompensationPercentile(UUID employeeId, int year) {
        TotalCompensationResponse statement = generateCompensationStatement(employeeId, year);
        BigDecimal employeeTotal = statement.totalCompensation();

        // Get all employees' compensation
        List<Employee> allEmployees = employeeRepository.findAll();
        List<BigDecimal> allCompensations = allEmployees
            .stream()
            .filter(e -> !e.getId().equals(employeeId))
            .map(e -> {
                try {
                    return generateCompensationStatement(e.getId(), year).totalCompensation();
                } catch (Exception ex) {
                    return BigDecimal.ZERO;
                }
            })
            .sorted()
            .toList();

        // Calculate percentile
        long belowCount = allCompensations.stream().filter(c -> c.compareTo(employeeTotal) < 0).count();

        double percentile = allCompensations.isEmpty() ? 50.0 : (belowCount * 100.0) / allCompensations.size();

        Map<String, Object> result = new HashMap<>();
        result.put("employeeId", employeeId);
        result.put("totalCompensation", employeeTotal);
        result.put("percentile", BigDecimal.valueOf(percentile).setScale(1, RoundingMode.HALF_UP));
        result.put("comparedTo", allCompensations.size());
        result.put("year", year);

        return result;
    }

    private BigDecimal calculateAnnualBaseSalary(UUID employeeId, int year) {
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        Optional<Compensation> compensation = compensationRepository
            .findAll(
                (Specification<Compensation>) (root, query, cb) -> {
                    if (query != null) {
                        query.orderBy(cb.desc(root.get("effectiveFrom")));
                    }
                    return cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), yearEnd)
                    );
                }
            )
            .stream()
            .findFirst();

        if (compensation.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Compensation comp = compensation.get();
        return switch (comp.getBasis()) {
            case ANNUAL -> comp.getBaseAmount();
            case MONTHLY -> comp.getBaseAmount().multiply(BigDecimal.valueOf(12));
            case HOURLY -> comp.getBaseAmount().multiply(BigDecimal.valueOf(2080)); // 40hrs * 52 weeks
        };
    }

    private BigDecimal calculateTotalBonuses(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        return bonusRepository
            .findAll(
                (Specification<Bonus>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("employee").get("id"), employeeId), cb.between(root.get("awardDate"), startDate, endDate))
            )
            .stream()
            .map(Bonus::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> getBonusByType(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        return bonusRepository
            .findAll(
                (Specification<Bonus>) (root, query, cb) ->
                    cb.and(cb.equal(root.get("employee").get("id"), employeeId), cb.between(root.get("awardDate"), startDate, endDate))
            )
            .stream()
            .collect(
                Collectors.groupingBy(b -> b.getType().name(), Collectors.reducing(BigDecimal.ZERO, Bonus::getAmount, BigDecimal::add))
            );
    }

    private BigDecimal calculateBenefitsValue(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        List<EmployeeBenefit> benefits = benefitRepository.findAll(
            (Specification<EmployeeBenefit>) (root, query, cb) ->
                cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.lessThanOrEqualTo(root.get("effectiveFrom"), endDate),
                    cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), startDate))
                )
        );

        // Sum employer costs (annualized)
        return benefits.stream().map(b -> b.getEmployerCost().multiply(BigDecimal.valueOf(12))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<String, BigDecimal> getBenefitsByType(UUID employeeId, LocalDate startDate, LocalDate endDate) {
        return benefitRepository
            .findAll(
                (Specification<EmployeeBenefit>) (root, query, cb) ->
                    cb.and(
                        cb.equal(root.get("employee").get("id"), employeeId),
                        cb.lessThanOrEqualTo(root.get("effectiveFrom"), endDate),
                        cb.or(cb.isNull(root.get("effectiveTo")), cb.greaterThanOrEqualTo(root.get("effectiveTo"), startDate))
                    )
            )
            .stream()
            .collect(
                Collectors.groupingBy(
                    b -> b.getType().name(),
                    Collectors.reducing(BigDecimal.ZERO, b -> b.getEmployerCost().multiply(BigDecimal.valueOf(12)), BigDecimal::add)
                )
            );
    }

    private List<TotalCompensationResponse.MonthlyCompensation> getMonthlyCompensation(UUID employeeId, int year) {
        List<PayrollResult> results = resultRepository.findAll(
            (Specification<PayrollResult>) (root, query, cb) -> {
                if (query != null) {
                    query.orderBy(cb.asc(root.get("payrollPeriod").get("startDate")));
                }
                return cb.and(
                    cb.equal(root.get("employee").get("id"), employeeId),
                    cb.greaterThanOrEqualTo(root.get("payrollPeriod").get("startDate"), LocalDate.of(year, 1, 1)),
                    cb.lessThanOrEqualTo(root.get("payrollPeriod").get("endDate"), LocalDate.of(year, 12, 31))
                );
            }
        );

        return results
            .stream()
            .map(r -> {
                Month month = r.getPayrollPeriod().getStartDate().getMonth();
                return new TotalCompensationResponse.MonthlyCompensation(
                    month.getValue(),
                    month.name(),
                    r.getGross(),
                    r.getNet(),
                    BigDecimal.ZERO, // Would need to aggregate bonuses by month
                    r.getTotalDeductions()
                );
            })
            .toList();
    }

    private TotalCompensationResponse.CompensationBreakdown calculateBreakdown(
        BigDecimal baseSalary,
        BigDecimal totalBonuses,
        BigDecimal totalBenefits,
        Map<String, BigDecimal> bonusByType,
        Map<String, BigDecimal> benefitsByType
    ) {
        BigDecimal total = baseSalary.add(totalBonuses).add(totalBenefits);

        BigDecimal basePct = total.compareTo(BigDecimal.ZERO) > 0
            ? baseSalary.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal bonusPct = total.compareTo(BigDecimal.ZERO) > 0
            ? totalBonuses.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        BigDecimal benefitsPct = total.compareTo(BigDecimal.ZERO) > 0
            ? totalBenefits.divide(total, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        return new TotalCompensationResponse.CompensationBreakdown(basePct, bonusPct, benefitsPct, bonusByType, benefitsByType);
    }

    private TotalCompensationResponse.YearOverYearComparison calculateYearComparison(UUID employeeId, int year) {
        try {
            TotalCompensationResponse previousYear = generateCompensationStatement(employeeId, year - 1);
            TotalCompensationResponse currentYear = generateCompensationStatement(employeeId, year);

            BigDecimal changeAmount = currentYear.totalCompensation().subtract(previousYear.totalCompensation());
            BigDecimal changePct = previousYear.totalCompensation().compareTo(BigDecimal.ZERO) > 0
                ? changeAmount.divide(previousYear.totalCompensation(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

            return new TotalCompensationResponse.YearOverYearComparison(
                previousYear.totalCompensation(),
                currentYear.totalCompensation(),
                changeAmount,
                changePct
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String getCurrencyCode(UUID employeeId) {
        return compensationRepository
            .findAll(
                (Specification<Compensation>) (root, query, cb) -> {
                    if (query != null) {
                        query.orderBy(cb.desc(root.get("effectiveFrom")));
                    }
                    return cb.equal(root.get("employee").get("id"), employeeId);
                }
            )
            .stream()
            .findFirst()
            .map(c -> c.getCurrency().getCode())
            .orElse("USD");
    }
}
