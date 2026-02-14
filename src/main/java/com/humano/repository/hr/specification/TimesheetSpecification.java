package com.humano.repository.hr.specification;

import com.humano.domain.hr.Employee;
import com.humano.domain.hr.Project;
import com.humano.domain.hr.Timesheet;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering Timesheet entities.
 * Provides comprehensive search capabilities across all Timesheet attributes.
 */
public class TimesheetSpecification {

    private TimesheetSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching timesheets with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param projectId Project ID filter
     * @param dateFrom Date from filter
     * @param dateTo Date to filter
     * @param minHours Minimum hours worked filter
     * @param maxHours Maximum hours worked filter
     * @param createdBy Created by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Timesheet> withCriteria(
        UUID employeeId,
        UUID projectId,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal minHours,
        BigDecimal maxHours,
        String createdBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<Timesheet, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by project ID
            if (projectId != null) {
                Join<Timesheet, Project> projectJoin = root.join("project");
                predicates.add(criteriaBuilder.equal(projectJoin.get("id"), projectId));
            }

            // Filter by date range
            if (dateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), dateTo));
            }

            // Filter by hours worked range
            if (minHours != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("hoursWorked"), minHours));
            }
            if (maxHours != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("hoursWorked"), maxHours));
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
    public static Specification<Timesheet> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Timesheet, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by project ID.
     *
     * @param projectId Project ID
     * @return Specification
     */
    public static Specification<Timesheet> hasProjectId(UUID projectId) {
        return (root, query, criteriaBuilder) -> {
            if (projectId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Timesheet, Project> projectJoin = root.join("project");
            return criteriaBuilder.equal(projectJoin.get("id"), projectId);
        };
    }
}
