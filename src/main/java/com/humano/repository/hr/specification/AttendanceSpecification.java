package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.AttendanceStatus;
import com.humano.domain.hr.Attendance;
import com.humano.domain.hr.Employee;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering Attendance entities.
 * Provides comprehensive search capabilities across all Attendance attributes.
 */
public class AttendanceSpecification {

    private AttendanceSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching attendance records with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param startDate Start date for date range filter
     * @param endDate End date for date range filter
     * @param status Attendance status filter
     * @param checkInFrom Minimum check-in time filter
     * @param checkInTo Maximum check-in time filter
     * @param checkOutFrom Minimum check-out time filter
     * @param checkOutTo Maximum check-out time filter
     * @param createdBy Created by user filter
     * @param lastModifiedBy Last modified by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<Attendance> withCriteria(
        UUID employeeId,
        LocalDate startDate,
        LocalDate endDate,
        AttendanceStatus status,
        LocalTime checkInFrom,
        LocalTime checkInTo,
        LocalTime checkOutFrom,
        LocalTime checkOutTo,
        String createdBy,
        String lastModifiedBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<Attendance, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by date range
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), endDate));
            }

            // Filter by attendance status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // Filter by check-in time range
            if (checkInFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("checkIn"), checkInFrom));
            }
            if (checkInTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("checkIn"), checkInTo));
            }

            // Filter by check-out time range
            if (checkOutFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("checkOut"), checkOutFrom));
            }
            if (checkOutTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("checkOut"), checkOutTo));
            }

            // Filter by created by
            if (createdBy != null && !createdBy.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy));
            }

            // Filter by last modified by
            if (lastModifiedBy != null && !lastModifiedBy.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("lastModifiedBy"), lastModifiedBy));
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
    public static Specification<Attendance> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<Attendance, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter by date range.
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Specification
     */
    public static Specification<Attendance> hasDateBetween(LocalDate startDate, LocalDate endDate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("date"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("date"), endDate));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by attendance status.
     *
     * @param status Attendance status
     * @return Specification
     */
    public static Specification<Attendance> hasStatus(AttendanceStatus status) {
        return (root, query, criteriaBuilder) -> {
            if (status == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("status"), status);
        };
    }

    /**
     * Filter by check-in time range.
     *
     * @param checkInFrom Minimum check-in time
     * @param checkInTo Maximum check-in time
     * @return Specification
     */
    public static Specification<Attendance> hasCheckInBetween(LocalTime checkInFrom, LocalTime checkInTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (checkInFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("checkIn"), checkInFrom));
            }
            if (checkInTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("checkIn"), checkInTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by check-out time range.
     *
     * @param checkOutFrom Minimum check-out time
     * @param checkOutTo Maximum check-out time
     * @return Specification
     */
    public static Specification<Attendance> hasCheckOutBetween(LocalTime checkOutFrom, LocalTime checkOutTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (checkOutFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("checkOut"), checkOutFrom));
            }
            if (checkOutTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("checkOut"), checkOutTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by created by user.
     *
     * @param createdBy Created by user
     * @return Specification
     */
    public static Specification<Attendance> hasCreatedBy(String createdBy) {
        return (root, query, criteriaBuilder) -> {
            if (createdBy == null || createdBy.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("createdBy"), createdBy);
        };
    }

    /**
     * Filter by last modified by user.
     *
     * @param lastModifiedBy Last modified by user
     * @return Specification
     */
    public static Specification<Attendance> hasLastModifiedBy(String lastModifiedBy) {
        return (root, query, criteriaBuilder) -> {
            if (lastModifiedBy == null || lastModifiedBy.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("lastModifiedBy"), lastModifiedBy);
        };
    }
}
