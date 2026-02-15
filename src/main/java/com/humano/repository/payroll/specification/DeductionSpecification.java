package com.humano.repository.payroll.specification;

import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.DeductionType;
import com.humano.domain.hr.Employee;
import com.humano.domain.payroll.Deduction;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * JPA Specification for filtering Deduction entities.
 * Provides comprehensive search capabilities across all Deduction attributes.
 */
public class DeductionSpecification {

    private DeductionSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching deductions with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param type Deduction type filter (TAX, INSURANCE, RETIREMENT, etc.)
     * @param currencyId Currency ID filter
     * @param minAmount Minimum deduction amount filter
     * @param maxAmount Maximum deduction amount filter
     * @param minPercentage Minimum deduction percentage filter
     * @param maxPercentage Maximum deduction percentage filter
     * @param effectiveFrom Effective from date filter (start range)
     * @param effectiveTo Effective to date filter (end range)
     * @param isPreTax Pre-tax status filter
     * @param activeOnly If true, only returns active deductions
     * @param description Description filter (partial match)
     * @param createdBy Created by filter (partial match)
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Deduction> withCriteria(
        UUID employeeId,
        DeductionType type,
        UUID currencyId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        BigDecimal minPercentage,
        BigDecimal maxPercentage,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean isPreTax,
        Boolean activeOnly,
        String description,
        String createdBy,
        Instant createdDateFrom,
        Instant createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<Deduction, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by deduction type
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }

            // Filter by currency ID
            if (currencyId != null) {
                Join<Deduction, Currency> currencyJoin = root.join("currency");
                predicates.add(criteriaBuilder.equal(currencyJoin.get("id"), currencyId));
            }

            // Filter by amount range
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            // Filter by percentage range
            if (minPercentage != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("percentage"), minPercentage));
            }
            if (maxPercentage != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("percentage"), maxPercentage));
            }

            // Filter by effective date range
            if (effectiveFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveFrom"), effectiveFrom));
            }
            if (effectiveTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("effectiveTo"), effectiveTo));
            }

            // Filter by pre-tax status
            if (isPreTax != null) {
                predicates.add(criteriaBuilder.equal(root.get("isPreTax"), isPreTax));
            }

            // Filter active deductions only
            if (Boolean.TRUE.equals(activeOnly)) {
                LocalDate today = LocalDate.now();
                predicates.add(
                    criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("effectiveTo")),
                        criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveTo"), today)
                    )
                );
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("effectiveFrom"), today));
            }

            // Filter by description (partial match, case-insensitive)
            if (StringUtils.hasText(description)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }

            // Filter by created by (partial match, case-insensitive)
            if (StringUtils.hasText(createdBy)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("createdBy")), "%" + createdBy.toLowerCase() + "%"));
            }

            // Filter by created date range
            if (createdDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            }
            if (createdDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdDate"), createdDateTo));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by employee ID.
     *
     * @param employeeId Employee ID
     * @return Specification
     */
    public static Specification<Deduction> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Deduction, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by deduction type.
     *
     * @param type Deduction type
     * @return Specification
     */
    public static Specification<Deduction> hasType(DeductionType type) {
        return (root, query, criteriaBuilder) -> {
            if (type == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("type"), type);
        };
    }

    /**
     * Filter by currency ID.
     *
     * @param currencyId Currency ID
     * @return Specification
     */
    public static Specification<Deduction> hasCurrencyId(UUID currencyId) {
        return (root, query, criteriaBuilder) -> {
            if (currencyId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Deduction, Currency> currencyJoin = root.join("currency");
            return criteriaBuilder.equal(currencyJoin.get("id"), currencyId);
        };
    }

    /**
     * Filter by amount range.
     *
     * @param minAmount Minimum amount
     * @param maxAmount Maximum amount
     * @return Specification
     */
    public static Specification<Deduction> hasAmountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by percentage range.
     *
     * @param minPercentage Minimum percentage
     * @param maxPercentage Maximum percentage
     * @return Specification
     */
    public static Specification<Deduction> hasPercentageBetween(BigDecimal minPercentage, BigDecimal maxPercentage) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minPercentage != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("percentage"), minPercentage));
            }
            if (maxPercentage != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("percentage"), maxPercentage));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by effective date range.
     *
     * @param effectiveFrom Effective from date
     * @param effectiveTo Effective to date
     * @return Specification
     */
    public static Specification<Deduction> hasEffectiveDateBetween(LocalDate effectiveFrom, LocalDate effectiveTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (effectiveFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveFrom"), effectiveFrom));
            }
            if (effectiveTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("effectiveTo"), effectiveTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by pre-tax status.
     *
     * @param isPreTax Pre-tax status
     * @return Specification
     */
    public static Specification<Deduction> hasIsPreTax(Boolean isPreTax) {
        return (root, query, criteriaBuilder) -> {
            if (isPreTax == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isPreTax"), isPreTax);
        };
    }

    /**
     * Filter for active deductions only (effectiveTo is null or in the future).
     *
     * @return Specification
     */
    public static Specification<Deduction> isActive() {
        return (root, query, criteriaBuilder) -> {
            LocalDate today = LocalDate.now();
            return criteriaBuilder.and(
                criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("effectiveTo")),
                    criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveTo"), today)
                ),
                criteriaBuilder.lessThanOrEqualTo(root.get("effectiveFrom"), today)
            );
        };
    }

    /**
     * Filter by description (partial match, case-insensitive).
     *
     * @param description Description to search for
     * @return Specification
     */
    public static Specification<Deduction> hasDescriptionContaining(String description) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(description)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%");
        };
    }

    /**
     * Filter by created by user (partial match, case-insensitive).
     *
     * @param createdBy Created by user
     * @return Specification
     */
    public static Specification<Deduction> hasCreatedBy(String createdBy) {
        return (root, query, criteriaBuilder) -> {
            if (!StringUtils.hasText(createdBy)) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("createdBy")), "%" + createdBy.toLowerCase() + "%");
        };
    }

    /**
     * Filter by created date range.
     *
     * @param createdDateFrom Created date from
     * @param createdDateTo Created date to
     * @return Specification
     */
    public static Specification<Deduction> hasCreatedDateBetween(Instant createdDateFrom, Instant createdDateTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (createdDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            }
            if (createdDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdDate"), createdDateTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
