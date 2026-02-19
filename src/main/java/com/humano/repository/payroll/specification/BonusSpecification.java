package com.humano.repository.payroll.specification;

import com.humano.domain.enumeration.payroll.BonusType;
import com.humano.domain.payroll.Bonus;
import com.humano.domain.payroll.Currency;
import com.humano.domain.shared.Employee;
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
 * JPA Specification for filtering Bonus entities.
 * Provides comprehensive search capabilities across all Bonus attributes.
 */
public class BonusSpecification {

    private BonusSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching bonuses with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param type Bonus type filter (PERFORMANCE, SIGNING, REFERRAL, etc.)
     * @param currencyId Currency ID filter
     * @param minAmount Minimum bonus amount filter
     * @param maxAmount Maximum bonus amount filter
     * @param awardDateFrom Award date from filter
     * @param awardDateTo Award date to filter
     * @param paymentDateFrom Payment date from filter
     * @param paymentDateTo Payment date to filter
     * @param isPaid Payment status filter
     * @param isTaxable Taxable status filter
     * @param description Description filter (partial match)
     * @param createdBy Created by filter (partial match)
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Bonus> withCriteria(
        UUID employeeId,
        BonusType type,
        UUID currencyId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDate awardDateFrom,
        LocalDate awardDateTo,
        LocalDate paymentDateFrom,
        LocalDate paymentDateTo,
        Boolean isPaid,
        Boolean isTaxable,
        String description,
        String createdBy,
        Instant createdDateFrom,
        Instant createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<Bonus, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by bonus type
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }

            // Filter by currency ID
            if (currencyId != null) {
                Join<Bonus, Currency> currencyJoin = root.join("currency");
                predicates.add(criteriaBuilder.equal(currencyJoin.get("id"), currencyId));
            }

            // Filter by minimum amount
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }

            // Filter by maximum amount
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            // Filter by award date range
            if (awardDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("awardDate"), awardDateFrom));
            }
            if (awardDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("awardDate"), awardDateTo));
            }

            // Filter by payment date range
            if (paymentDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paymentDate"), paymentDateFrom));
            }
            if (paymentDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("paymentDate"), paymentDateTo));
            }

            // Filter by payment status
            if (isPaid != null) {
                predicates.add(criteriaBuilder.equal(root.get("isPaid"), isPaid));
            }

            // Filter by taxable status
            if (isTaxable != null) {
                predicates.add(criteriaBuilder.equal(root.get("isTaxable"), isTaxable));
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
    public static Specification<Bonus> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Bonus, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by bonus type.
     *
     * @param type Bonus type
     * @return Specification
     */
    public static Specification<Bonus> hasType(BonusType type) {
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
    public static Specification<Bonus> hasCurrencyId(UUID currencyId) {
        return (root, query, criteriaBuilder) -> {
            if (currencyId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Bonus, Currency> currencyJoin = root.join("currency");
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
    public static Specification<Bonus> hasAmountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
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
     * Filter by award date range.
     *
     * @param awardDateFrom Award date from
     * @param awardDateTo Award date to
     * @return Specification
     */
    public static Specification<Bonus> hasAwardDateBetween(LocalDate awardDateFrom, LocalDate awardDateTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (awardDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("awardDate"), awardDateFrom));
            }
            if (awardDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("awardDate"), awardDateTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by payment date range.
     *
     * @param paymentDateFrom Payment date from
     * @param paymentDateTo Payment date to
     * @return Specification
     */
    public static Specification<Bonus> hasPaymentDateBetween(LocalDate paymentDateFrom, LocalDate paymentDateTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (paymentDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paymentDate"), paymentDateFrom));
            }
            if (paymentDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("paymentDate"), paymentDateTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by payment status.
     *
     * @param isPaid Payment status
     * @return Specification
     */
    public static Specification<Bonus> hasIsPaid(Boolean isPaid) {
        return (root, query, criteriaBuilder) -> {
            if (isPaid == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isPaid"), isPaid);
        };
    }

    /**
     * Filter by taxable status.
     *
     * @param isTaxable Taxable status
     * @return Specification
     */
    public static Specification<Bonus> hasIsTaxable(Boolean isTaxable) {
        return (root, query, criteriaBuilder) -> {
            if (isTaxable == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("isTaxable"), isTaxable);
        };
    }

    /**
     * Filter by description (partial match, case-insensitive).
     *
     * @param description Description to search for
     * @return Specification
     */
    public static Specification<Bonus> hasDescriptionContaining(String description) {
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
    public static Specification<Bonus> hasCreatedBy(String createdBy) {
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
    public static Specification<Bonus> hasCreatedDateBetween(Instant createdDateFrom, Instant createdDateTo) {
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
