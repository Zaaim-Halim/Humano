package com.humano.repository.hr.workflow;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.hr.ReviewCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ReviewCycle entity.
 */
@Repository
public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {
    /**
     * Find active review cycles.
     */
    List<ReviewCycle> findByActiveTrue();

    /**
     * Find review cycles by phase.
     */
    List<ReviewCycle> findByPhaseAndActiveTrue(ReviewCyclePhase phase);

    /**
     * Find review cycles within date range.
     */
    Page<ReviewCycle> findByStartDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    /**
     * Find current active cycle.
     */
    @Query("SELECT r FROM ReviewCycle r WHERE r.active = true AND r.startDate <= :date AND r.endDate >= :date")
    Optional<ReviewCycle> findCurrentActiveCycle(@Param("date") LocalDate date);

    /**
     * Find cycles by department.
     */
    @Query("SELECT r FROM ReviewCycle r WHERE :departmentId MEMBER OF r.departmentIds AND r.active = true")
    List<ReviewCycle> findByDepartmentId(@Param("departmentId") UUID departmentId);

    /**
     * Find cycles with approaching deadlines.
     */
    @Query(
        "SELECT r FROM ReviewCycle r WHERE r.active = true AND " +
        "((r.phase = 'SELF_ASSESSMENT' AND r.selfAssessmentDeadline <= :deadline) OR " +
        "(r.phase = 'MANAGER_REVIEW' AND r.managerReviewDeadline <= :deadline) OR " +
        "(r.phase = 'CALIBRATION' AND r.calibrationDeadline <= :deadline) OR " +
        "(r.phase = 'FEEDBACK_DELIVERY' AND r.feedbackDeadline <= :deadline))"
    )
    List<ReviewCycle> findCyclesWithApproachingDeadlines(@Param("deadline") LocalDate deadline);

    /**
     * Check if a cycle exists with overlapping dates.
     */
    @Query(
        "SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM ReviewCycle r " +
        "WHERE r.active = true AND r.id != :excludeId AND " +
        "((r.startDate <= :endDate AND r.endDate >= :startDate))"
    )
    boolean existsOverlappingCycle(
        @Param("excludeId") UUID excludeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find by name.
     */
    Optional<ReviewCycle> findByName(String name);
}
