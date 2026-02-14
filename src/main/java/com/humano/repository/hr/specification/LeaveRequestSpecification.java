package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.LeaveStatus;
import com.humano.domain.enumeration.hr.LeaveType;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.LeaveRequest;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering LeaveRequest entities.
 * Provides comprehensive search capabilities across all LeaveRequest attributes.
 */
public class LeaveRequestSpecification {

    private LeaveRequestSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching leave requests with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param approverId Approver ID filter
     * @param leaveType Leave type filter
     * @param status Leave status filter
     * @param startDateFrom Start date from filter
     * @param startDateTo Start date to filter
     * @param endDateFrom End date from filter
     * @param endDateTo End date to filter
     * @param reason Reason filter (partial match)
     * @param minDaysCount Minimum days count filter
     * @param maxDaysCount Maximum days count filter
     * @param createdBy Created by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<LeaveRequest> withCriteria(
        UUID employeeId,
        UUID approverId,
        LeaveType leaveType,
        LeaveStatus status,
        LocalDate startDateFrom,
        LocalDate startDateTo,
        LocalDate endDateFrom,
        LocalDate endDateTo,
        String reason,
        Integer minDaysCount,
        Integer maxDaysCount,
        String createdBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<LeaveRequest, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by approver ID
            if (approverId != null) {
                Join<LeaveRequest, Employee> approverJoin = root.join("approver");
                predicates.add(criteriaBuilder.equal(approverJoin.get("id"), approverId));
            }

            // Filter by leave type
            if (leaveType != null) {
                predicates.add(criteriaBuilder.equal(root.get("leaveType"), leaveType));
            }

            // Filter by leave status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // Filter by start date range
            if (startDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("startDate"), startDateFrom));
            }
            if (startDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startDate"), startDateTo));
            }

            // Filter by end date range
            if (endDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("endDate"), endDateFrom));
            }
            if (endDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), endDateTo));
            }

            // Filter by reason (partial match, case-insensitive)
            if (reason != null && !reason.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("reason")), "%" + reason.toLowerCase() + "%"));
            }

            // Filter by days count range
            if (minDaysCount != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("daysCount"), minDaysCount));
            }
            if (maxDaysCount != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("daysCount"), maxDaysCount));
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
    public static Specification<LeaveRequest> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<LeaveRequest, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by leave status.
     *
     * @param status Leave status
     * @return Specification
     */
    public static Specification<LeaveRequest> hasStatus(LeaveStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /**
     * Filter pending leave requests.
     *
     * @return Specification
     */
    public static Specification<LeaveRequest> isPending() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), LeaveStatus.PENDING);
    }
}
