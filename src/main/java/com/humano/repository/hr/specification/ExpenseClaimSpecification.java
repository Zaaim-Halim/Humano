package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.ExpenseClaim;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering ExpenseClaim entities.
 * Provides comprehensive search capabilities across all ExpenseClaim attributes.
 */
public class ExpenseClaimSpecification {

    private ExpenseClaimSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching expense claims with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param status Expense claim status filter
     * @param claimDateFrom Claim date from filter
     * @param claimDateTo Claim date to filter
     * @param minAmount Minimum amount filter
     * @param maxAmount Maximum amount filter
     * @param description Description filter (partial match)
     * @param hasReceipt Receipt presence filter
     * @param createdBy Created by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<ExpenseClaim> withCriteria(
        UUID employeeId,
        ExpenseClaimStatus status,
        LocalDate claimDateFrom,
        LocalDate claimDateTo,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String description,
        Boolean hasReceipt,
        String createdBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<ExpenseClaim, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by expense claim status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // Filter by claim date range
            if (claimDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("claimDate"), claimDateFrom));
            }
            if (claimDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("claimDate"), claimDateTo));
            }

            // Filter by amount range
            if (minAmount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }
            if (maxAmount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            // Filter by description (partial match, case-insensitive)
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }

            // Filter by receipt presence
            if (hasReceipt != null) {
                if (hasReceipt) {
                    predicates.add(criteriaBuilder.isNotNull(root.get("receiptUrl")));
                } else {
                    predicates.add(criteriaBuilder.isNull(root.get("receiptUrl")));
                }
            }

            // Filter by created by
            if (createdBy != null && !createdBy.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy));
            }

            // Filter by created date range
            if (createdDateFrom != null) {
                predicates.add(
                    criteriaBuilder.greaterThanOrEqualTo(
                        criteriaBuilder.function("DATE", LocalDate.class, root.get("createdDate")),
                        createdDateFrom
                    )
                );
            }
            if (createdDateTo != null) {
                predicates.add(
                    criteriaBuilder.lessThanOrEqualTo(
                        criteriaBuilder.function("DATE", LocalDate.class, root.get("createdDate")),
                        createdDateTo
                    )
                );
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
    public static Specification<ExpenseClaim> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<ExpenseClaim, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by expense claim status.
     *
     * @param status Expense claim status
     * @return Specification
     */
    public static Specification<ExpenseClaim> hasStatus(ExpenseClaimStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /**
     * Filter pending expense claims.
     *
     * @return Specification
     */
    public static Specification<ExpenseClaim> isPending() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), ExpenseClaimStatus.PENDING);
    }

    /**
     * Filter expense claims with receipts.
     *
     * @return Specification
     */
    public static Specification<ExpenseClaim> hasReceipt() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNotNull(root.get("receiptUrl"));
    }
}
