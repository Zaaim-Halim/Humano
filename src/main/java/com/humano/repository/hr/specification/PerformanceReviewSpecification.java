package com.humano.repository.hr.specification;

import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.shared.Employee;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering PerformanceReview entities.
 * Provides comprehensive search capabilities across all PerformanceReview attributes.
 */
public class PerformanceReviewSpecification {

    private PerformanceReviewSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching performance reviews with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param reviewerId Reviewer ID filter
     * @param reviewDateFrom Review date from filter
     * @param reviewDateTo Review date to filter
     * @param minRating Minimum rating filter
     * @param maxRating Maximum rating filter
     * @param comments Comments filter (partial match)
     * @param createdBy Created by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<PerformanceReview> withCriteria(
        UUID employeeId,
        UUID reviewerId,
        LocalDate reviewDateFrom,
        LocalDate reviewDateTo,
        Integer minRating,
        Integer maxRating,
        String comments,
        String createdBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<PerformanceReview, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by reviewer ID
            if (reviewerId != null) {
                Join<PerformanceReview, Employee> reviewerJoin = root.join("reviewer");
                predicates.add(criteriaBuilder.equal(reviewerJoin.get("id"), reviewerId));
            }

            // Filter by review date range
            if (reviewDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("reviewDate"), reviewDateFrom));
            }
            if (reviewDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("reviewDate"), reviewDateTo));
            }

            // Filter by rating range
            if (minRating != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("rating"), minRating));
            }
            if (maxRating != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("rating"), maxRating));
            }

            // Filter by comments (partial match, case-insensitive)
            if (comments != null && !comments.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("comments")), "%" + comments.toLowerCase() + "%"));
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
    public static Specification<PerformanceReview> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<PerformanceReview, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by reviewer ID.
     *
     * @param reviewerId Reviewer ID
     * @return Specification
     */
    public static Specification<PerformanceReview> hasReviewerId(UUID reviewerId) {
        return (root, query, criteriaBuilder) -> {
            if (reviewerId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<PerformanceReview, Employee> reviewerJoin = root.join("reviewer");
            return criteriaBuilder.equal(reviewerJoin.get("id"), reviewerId);
        };
    }
}
