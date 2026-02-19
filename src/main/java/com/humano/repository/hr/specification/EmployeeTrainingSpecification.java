package com.humano.repository.hr.specification;

import com.humano.domain.enumeration.hr.TrainingStatus;
import com.humano.domain.hr.EmployeeTraining;
import com.humano.domain.hr.Training;
import com.humano.domain.shared.Employee;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification for filtering EmployeeTraining entities.
 * Provides comprehensive search capabilities across all EmployeeTraining attributes.
 */
public class EmployeeTrainingSpecification {

    private EmployeeTrainingSpecification() {
        // Private constructor to prevent instantiation
    }

    /**
     * Creates a specification for searching employee training records with multiple criteria.
     *
     * @param employeeId Employee ID filter
     * @param trainingId Training ID filter
     * @param status Training status filter
     * @param completionDateFrom Completion date from filter
     * @param completionDateTo Completion date to filter
     * @param description Description filter (partial match)
     * @param feedback Feedback filter (partial match)
     * @return Specification for the given criteria
     */
    public static Specification<EmployeeTraining> withCriteria(
        UUID employeeId,
        UUID trainingId,
        TrainingStatus status,
        LocalDate completionDateFrom,
        LocalDate completionDateTo,
        String description,
        String feedback
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Filter by employee ID
            if (employeeId != null) {
                Join<EmployeeTraining, Employee> employeeJoin = root.join("employee");
                predicates.add(criteriaBuilder.equal(employeeJoin.get("id"), employeeId));
            }

            // Filter by training ID
            if (trainingId != null) {
                Join<EmployeeTraining, Training> trainingJoin = root.join("training");
                predicates.add(criteriaBuilder.equal(trainingJoin.get("id"), trainingId));
            }

            // Filter by status
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // Filter by completion date range
            if (completionDateFrom != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("completionDate"), completionDateFrom));
            }
            if (completionDateTo != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("completionDate"), completionDateTo));
            }

            // Filter by description (partial match, case-insensitive)
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }

            // Filter by feedback (partial match, case-insensitive)
            if (feedback != null && !feedback.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("feedback")), "%" + feedback.toLowerCase() + "%"));
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
    public static Specification<EmployeeTraining> hasEmployeeId(UUID employeeId) {
        return (root, query, criteriaBuilder) -> {
            if (employeeId == null) {
                return criteriaBuilder.conjunction();
            }
            Join<EmployeeTraining, Employee> employeeJoin = root.join("employee");
            return criteriaBuilder.equal(employeeJoin.get("id"), employeeId);
        };
    }

    /**
     * Filter completed training records.
     *
     * @return Specification
     */
    public static Specification<EmployeeTraining> isCompleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), TrainingStatus.COMPLETED);
    }
}
