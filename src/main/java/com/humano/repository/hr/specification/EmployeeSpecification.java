package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.hr.Department;
import com.humano.domain.hr.Employee;
import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering Employee entities.
 * Provides comprehensive search capabilities across all Employee attributes.
 */
public class EmployeeSpecification {

    private EmployeeSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching employees with multiple criteria.
     *
     * @param firstName First name filter (partial match)
     * @param lastName Last name filter (partial match)
     * @param email Email filter (partial match)
     * @param jobTitle Job title filter (partial match)
     * @param phone Phone number filter (partial match)
     * @param status Employee status filter
     * @param departmentId Department ID filter
     * @param positionId Position ID filter
     * @param unitId Organizational unit ID filter
     * @param managerId Manager ID filter
     * @param startDateFrom Start date from filter
     * @param startDateTo Start date to filter
     * @param endDateFrom End date from filter
     * @param endDateTo End date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Employee> withCriteria(
        String firstName,
        String lastName,
        String email,
        String jobTitle,
        String phone,
        EmployeeStatus status,
        UUID departmentId,
        UUID positionId,
        UUID unitId,
        UUID managerId,
        LocalDate startDateFrom,
        LocalDate startDateTo,
        LocalDate endDateFrom,
        LocalDate endDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by first name (partial match, case-insensitive)
            if (firstName != null && !firstName.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), "%" + firstName.toLowerCase() + "%"));
            }

            // Filter by last name (partial match, case-insensitive)
            if (lastName != null && !lastName.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), "%" + lastName.toLowerCase() + "%"));
            }

            // Filter by email (partial match, case-insensitive)
            if (email != null && !email.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }

            // Filter by job title (partial match, case-insensitive)
            if (jobTitle != null && !jobTitle.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("jobTitle")), "%" + jobTitle.toLowerCase() + "%"));
            }

            // Filter by phone (partial match)
            if (phone != null && !phone.isEmpty()) {
                predicates.add(criteriaBuilder.like(root.get("phone"), "%" + phone + "%"));
            }

            // Filter by employee status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // Filter by department ID
            if (departmentId != null) {
                Join<Employee, Department> departmentJoin = root.join("department");
                predicates.add(criteriaBuilder.equal(departmentJoin.get("id"), departmentId));
            }

            // Filter by position ID
            if (positionId != null) {
                Join<Employee, Position> positionJoin = root.join("position");
                predicates.add(criteriaBuilder.equal(positionJoin.get("id"), positionId));
            }

            // Filter by organizational unit ID
            if (unitId != null) {
                Join<Employee, OrganizationalUnit> unitJoin = root.join("unit");
                predicates.add(criteriaBuilder.equal(unitJoin.get("id"), unitId));
            }

            // Filter by manager ID
            if (managerId != null) {
                Join<Employee, Employee> managerJoin = root.join("manager");
                predicates.add(criteriaBuilder.equal(managerJoin.get("id"), managerId));
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

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter employees by active status.
     *
     * @return Specification
     */
    public static Specification<Employee> isActive() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), EmployeeStatus.ACTIVE);
    }

    /**
     * Filter employees by department.
     *
     * @param departmentId Department ID
     * @return Specification
     */
    public static Specification<Employee> hasDepartmentId(UUID departmentId) {
        return (root, query, criteriaBuilder) -> {
            if (departmentId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Employee, Department> departmentJoin = root.join("department");
            return criteriaBuilder.equal(departmentJoin.get("id"), departmentId);
        };
    }

    /**
     * Filter employees by manager.
     *
     * @param managerId Manager ID
     * @return Specification
     */
    public static Specification<Employee> hasManagerId(UUID managerId) {
        return (root, query, criteriaBuilder) -> {
            if (managerId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Employee, Employee> managerJoin = root.join("manager");
            return criteriaBuilder.equal(managerJoin.get("id"), managerId);
        };
    }
}
