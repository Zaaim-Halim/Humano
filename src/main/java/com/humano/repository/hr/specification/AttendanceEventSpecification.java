package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.EventAction;
import com.humano.domain.enumeration.hr.EventType;
import com.humano.domain.hr.Attendance;
import com.humano.domain.hr.AttendanceEvent;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering AttendanceEvent entities.
 * Provides comprehensive search capabilities across all AttendanceEvent attributes.
 */
public class AttendanceEventSpecification {

    private AttendanceEventSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching attendance events with multiple criteria.
     *
     * @param attendanceId Attendance ID filter
     * @param employeeId Employee ID filter (through attendance)
     * @param eventType Event type filter
     * @param eventAction Event action filter
     * @param eventTimeFrom Minimum event time filter
     * @param eventTimeTo Maximum event time filter
     * @param description Description filter (partial match)
     * @param createdBy Created by user filter
     * @param lastModifiedBy Last modified by user filter
     * @param createdDateFrom Created date from filter
     * @param createdDateTo Created date to filter
     * @return Specification for the given criteria
     */
    public static Specification<AttendanceEvent> withCriteria(
        UUID attendanceId,
        UUID employeeId,
        EventType eventType,
        EventAction eventAction,
        LocalTime eventTimeFrom,
        LocalTime eventTimeTo,
        String description,
        String createdBy,
        String lastModifiedBy,
        LocalDate createdDateFrom,
        LocalDate createdDateTo
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by attendance ID
            if (attendanceId != null) {
                Join<AttendanceEvent, Attendance> attendanceJoin = root.join("attendance");
                predicates.add(criteriaBuilder.equal(attendanceJoin.get("id"), attendanceId));
            }

            // Filter by employee ID (through attendance)
            if (employeeId != null) {
                Join<AttendanceEvent, Attendance> attendanceJoin = root.join("attendance");
                predicates.add(criteriaBuilder.equal(attendanceJoin.get("employee").get("id"), employeeId));
            }

            // Filter by event type
            if (eventType != null) {
                predicates.add(criteriaBuilder.equal(root.get("eventType"), eventType));
            }

            // Filter by event action
            if (eventAction != null) {
                predicates.add(criteriaBuilder.equal(root.get("eventAction"), eventAction));
            }

            // Filter by event time range
            if (eventTimeFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("eventTime"), eventTimeFrom));
            }
            if (eventTimeTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("eventTime"), eventTimeTo));
            }

            // Filter by description (partial match, case-insensitive)
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
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
     * Filter by attendance ID.
     *
     * @param attendanceId Attendance ID
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasAttendanceId(UUID attendanceId) {
        return (root, query, criteriaBuilder) -> {
            if (attendanceId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<AttendanceEvent, Attendance> attendanceJoin = root.join("attendance");
            return criteriaBuilder.equal(attendanceJoin.get("id"), attendanceId);
        };
    }

    /**
     * Filter by employee ID (through attendance relationship).
     *
     * @param employeeId Employee ID
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<AttendanceEvent, Attendance> attendanceJoin = root.join("attendance");
            return criteriaBuilder.equal(attendanceJoin.get("employee").get("id"), employeeId);
        };
    }

    /**
     * Filter by event type.
     *
     * @param eventType Event type
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasEventType(EventType eventType) {
        return (root, query, criteriaBuilder) -> {
            if (eventType == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("eventType"), eventType);
        };
    }

    /**
     * Filter by event action.
     *
     * @param eventAction Event action
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasEventAction(EventAction eventAction) {
        return (root, query, criteriaBuilder) -> {
            if (eventAction == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("eventAction"), eventAction);
        };
    }

    /**
     * Filter by event time range.
     *
     * @param eventTimeFrom Minimum event time
     * @param eventTimeTo Maximum event time
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasEventTimeBetween(LocalTime eventTimeFrom, LocalTime eventTimeTo) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (eventTimeFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("eventTime"), eventTimeFrom));
            }
            if (eventTimeTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("eventTime"), eventTimeTo));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Filter by description (partial match, case-insensitive).
     *
     * @param description Description to search for
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasDescriptionContaining(String description) {
        return (root, query, criteriaBuilder) -> {
            if (description == null || description.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%");
        };
    }

    /**
     * Filter by created by user.
     *
     * @param createdBy Created by user
     * @return Specification
     */
    public static Specification<AttendanceEvent> hasCreatedBy(String createdBy) {
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
    public static Specification<AttendanceEvent> hasLastModifiedBy(String lastModifiedBy) {
        return (root, query, criteriaBuilder) -> {
            if (lastModifiedBy == null || lastModifiedBy.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("lastModifiedBy"), lastModifiedBy);
        };
    }
}
