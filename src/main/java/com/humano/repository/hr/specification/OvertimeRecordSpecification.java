package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.OvertimeApprovalStatus;
import com.humano.domain.enumeration.hr.OvertimeType;
import com.humano.domain.hr.OvertimeRecord;
import com.humano.domain.shared.Employee;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering OvertimeRecord entities.
 * Provides comprehensive search capabilities across all OvertimeRecord attributes.
 */
public class OvertimeRecordSpecification {

    private OvertimeRecordSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching overtime records with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param approvedById Approver ID filter
     * @param overtimeType Overtime type filter
     * @param approvalStatus Approval status filter
     * @param dateFrom Date from filter
     * @param dateTo Date to filter
     * @param minHours Minimum hours filter
     * @param maxHours Maximum hours filter
     * @param notes Notes filter (partial match)
     * @param createdBy Created by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<OvertimeRecord> withCriteria(
        UUID employeeId,
        UUID approvedById,
        OvertimeType overtimeType,
        OvertimeApprovalStatus approvalStatus,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal minHours,
        BigDecimal maxHours,
        String notes,
        String createdBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<OvertimeRecord, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by approver ID
            if (approvedById != null) {
                Join<OvertimeRecord, Employee> approverJoin = root.join("approvedBy");
                predicates.add(criteriaBuilder.equal(approverJoin.get("id"), approvedById));
            }

            // Filter by overtime type
            if (overtimeType != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), overtimeType));
            }

            // Filter by approval status
            if (approvalStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("approvalStatus"), approvalStatus));
            }

            // Filter by date range
            if (dateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), dateTo));
            }

            // Filter by hours range
            if (minHours != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("hours"), minHours));
            }
            if (maxHours != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("hours"), maxHours));
            }

            // Filter by notes (partial match, case-insensitive)
            if (notes != null && !notes.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("notes")), "%" + notes.toLowerCase() + "%"));
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
    public static Specification<OvertimeRecord> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<OvertimeRecord, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter pending overtime records.
     *
     * @return Specification
     */
    public static Specification<OvertimeRecord> isPending() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("approvalStatus"), OvertimeApprovalStatus.PENDING);
    }
}
