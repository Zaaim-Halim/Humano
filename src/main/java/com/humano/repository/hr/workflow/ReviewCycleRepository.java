package com.humano.repository.hr.workflow;

import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.domain.hr.ReviewCycle;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * Active cycles running on a date, earliest first — several may (a probation cycle during the
     * regular one, or regular cycles of different departments).
     */
    @Query("SELECT r FROM ReviewCycle r WHERE r.active = true AND r.startDate <= :date AND r.endDate >= :date ORDER BY r.startDate, r.name")
    List<ReviewCycle> findActiveOn(@Param("date") LocalDate date);

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
     * Active cycles of a type, other than {@code excludeId} (nullable), whose dates overlap the
     * given ones — whether their people overlap is for the caller to check.
     */
    @Query(
        "SELECT r FROM ReviewCycle r " +
        "WHERE r.active = true AND r.type = :type AND (:excludeId IS NULL OR r.id <> :excludeId) AND " +
        "r.startDate <= :endDate AND r.endDate >= :startDate"
    )
    List<ReviewCycle> findActiveOverlapping(
        @Param("type") ReviewCycleType type,
        @Param("excludeId") UUID excludeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /** How many cycles use a review template. */
    long countByTemplateId(UUID templateId);

    /**
     * Whether a cycle of a type (in any phase) already reviews this period.
     */
    boolean existsByTypeAndReviewPeriodStartAndReviewPeriodEnd(
        ReviewCycleType type,
        LocalDate reviewPeriodStart,
        LocalDate reviewPeriodEnd
    );

    /**
     * Find by name.
     */
    Optional<ReviewCycle> findByName(String name);

    /** The cycles of a type, latest period first — the regular ones are what pay decisions follow. */
    List<ReviewCycle> findByTypeOrderByReviewPeriodEndDescStartDateDesc(ReviewCycleType type);

    /** Cycles whose review period ended before a date, except one phase (drafts), oldest first. */
    List<ReviewCycle> findByReviewPeriodEndBeforeAndPhaseNotOrderByReviewPeriodEndAsc(LocalDate date, ReviewCyclePhase phase);

    /** One cycle, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReviewCycle c where c.id = :id")
    Optional<ReviewCycle> findForUpdate(@Param("id") UUID id);
}
