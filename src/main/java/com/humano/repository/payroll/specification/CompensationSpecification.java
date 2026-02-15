package com.humano.repository.payroll.specification;

import com.humano.domain.Currency;
import com.humano.domain.enumeration.payroll.Basis;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.Position;
import com.humano.domain.payroll.Compensation;
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
 * JPA Specification for filtering Compensation entities.
 * Provides comprehensive search capabilities across all Compensation attributes.
 */
public class CompensationSpecification {

    private CompensationSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching compensations with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param positionId Position ID filter
     * @param currencyId Currency ID filter
     * @param basis Compensation basis filter (MONTHLY, ANNUAL, HOURLY)
     * @param minAmount Minimum base amount filter
     * @param maxAmount Maximum base amount filter
     * @param effectiveFrom Effective from date filter (start range)
     * @param effectiveTo Effective to date filter (end range)
     * @param activeOnly If true, only returns compensations without an effectiveTo date or with effectiveTo in the future
     * @param createdBy Created by filter (partial match)
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Compensation> withCriteria(
        UUID employeeId,
        UUID positionId,
        UUID currencyId,
        Basis basis,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean activeOnly,
        String createdBy,
        Instant createdDateFrom,
        Instant createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<Compensation, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by position ID
            if (positionId != null) {
                Join<Compensation, Position> positionJoin = root.join("position");
                predicates.add(criteriaBuilder.equal(positionJoin.get("id"), positionId));
            }

            // Filter by currency ID
            if (currencyId != null) {
                Join<Compensation, Currency> currencyJoin = root.join("currency");
                predicates.add(criteriaBuilder.equal(currencyJoin.get("id"), currencyId));
            }

            // Filter by basis
            if (basis != null) {
                predicates.add(criteriaBuilder.equal(root.get("basis"), basis));
            }

            // Filter by minimum amount
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("baseAmount"), minAmount));
            }

            // Filter by maximum amount
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("baseAmount"), maxAmount));
            }

            // Filter by effective from date
            if (effectiveFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("effectiveFrom"), effectiveFrom));
            }

            // Filter by effective to date
            if (effectiveTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("effectiveTo"), effectiveTo));
            }

            // Filter active compensations only
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

            // Filter by created by (partial match, case-insensitive)
            if (StringUtils.hasText(createdBy)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("createdBy")), "%" + createdBy.toLowerCase() + "%"));
            }

            // Filter by created date range
            if (createdDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            }

            // Filter by created date to
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
    public static Specification<Compensation> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Compensation, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by position ID.
     *
     * @param positionId Position ID
     * @return Specification
     */
    public static Specification<Compensation> hasPositionId(UUID positionId) {
        return (root, query, criteriaBuilder) -> {
            if (positionId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Compensation, Position> positionJoin = root.join("position");
            return criteriaBuilder.equal(positionJoin.get("id"), positionId);
        };
    }

    /**
     * Filter by currency ID.
     *
     * @param currencyId Currency ID
     * @return Specification
     */
    public static Specification<Compensation> hasCurrencyId(UUID currencyId) {
        return (root, query, criteriaBuilder) -> {
            if (currencyId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Compensation, Currency> currencyJoin = root.join("currency");
            return criteriaBuilder.equal(currencyJoin.get("id"), currencyId);
        };
    }

    /**
     * Filter by basis.
     *
     * @param basis Compensation basis
     * @return Specification
     */
    public static Specification<Compensation> hasBasis(Basis basis) {
        return (root, query, criteriaBuilder) -> {
            if (basis == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("basis"), basis);
        };
    }

    /**
     * Filter by base amount range.
     *
     * @param minAmount Minimum base amount
     * @param maxAmount Maximum base amount
     * @return Specification
     */
    public static Specification<Compensation> hasBaseAmountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("baseAmount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("baseAmount"), maxAmount));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by effective from date range.
     *
     * @param effectiveFrom Effective from date (start range)
     * @param effectiveTo Effective to date (end range)
     * @return Specification
     */
    public static Specification<Compensation> hasEffectiveDateBetween(LocalDate effectiveFrom, LocalDate effectiveTo) {
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
     * Filter for active compensations only (effectiveTo is null or in the future).
     *
     * @return Specification
     */
    public static Specification<Compensation> isActive() {
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
     * Filter by created by user (partial match, case-insensitive).
     *
     * @param createdBy Created by user
     * @return Specification
     */
    public static Specification<Compensation> hasCreatedBy(String createdBy) {
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
    public static Specification<Compensation> hasCreatedDateBetween(Instant createdDateFrom, Instant createdDateTo) {
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
