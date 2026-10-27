package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.SignOffStatus;
import com.humano.domain.hr.PerformanceReview;
import com.humano.repository.hr.projection.ReviewProgressRow;
import com.humano.repository.hr.projection.ReviewStatRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PerformanceReview} entity.
 * <p>
 * Every read that is mapped to a response fetches the employee, the reviewer and the cycle, so a
 * page of reviews costs one SELECT (plus the count) instead of up to three per row.
 */
@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID>, JpaSpecificationExecutor<PerformanceReview> {
    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Page<PerformanceReview> findByEmployeeId(UUID employeeId, Pageable pageable);

    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Page<PerformanceReview> findByReviewerId(UUID reviewerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Page<PerformanceReview> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Page<PerformanceReview> findAll(Specification<PerformanceReview> spec, Pageable pageable);

    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Optional<PerformanceReview> findByCycleIdAndEmployeeId(UUID cycleId, UUID employeeId);

    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Optional<PerformanceReview> findByIdAndCycleId(UUID id, UUID cycleId);

    /** A cycle review with what its page shows; {@code empty} for a review written outside a cycle. */
    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    Optional<PerformanceReview> findByIdAndCycleIsNotNull(UUID id);

    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    List<PerformanceReview> findByCycleId(UUID cycleId);

    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    List<PerformanceReview> findByCycleIdAndStatusIn(UUID cycleId, Collection<PerformanceReviewStatus> statuses);

    /** The caller's own reviews in cycles that are still running. */
    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    List<PerformanceReview> findByEmployeeIdAndCycleActiveTrueOrderByReviewDateDesc(UUID employeeId);

    /** Reviews the caller has to write, in cycles that are still running. */
    @EntityGraph(attributePaths = { "employee", "reviewer", "cycle" })
    List<PerformanceReview> findByReviewerIdAndCycleActiveTrueOrderByReviewDateDesc(UUID reviewerId);

    long countByCycleId(UUID cycleId);

    long countByCycleIdAndAcknowledgedAtIsNotNull(UUID cycleId);

    /** Disputes in a cycle still waiting for HR. */
    long countByCycleIdAndDisputedTrueAndDisputeResolvedAtIsNull(UUID cycleId);

    /** Reviews of a cycle at a sign-off status, for its progress. */
    long countByCycleIdAndSignOffStatus(UUID cycleId, SignOffStatus signOffStatus);

    @Query("SELECT r.employee.id FROM PerformanceReview r WHERE r.cycle.id = :cycleId")
    Set<UUID> findEmployeeIdsByCycleId(@Param("cycleId") UUID cycleId);

    /** The reviews of some cycles, reduced to what performance analytics counts; excluded reviews don't count. */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.ReviewStatRow(
            r.id, r.cycle.id, d.id, d.name, r.status, r.selfSubmittedAt, r.managerSubmittedAt, r.acknowledgedAt,
            r.disputed, r.managerRating, r.rating, r.recommendForPromotion
        )
        FROM PerformanceReview r
        JOIN r.employee e
        LEFT JOIN e.department d
        WHERE r.cycle.id IN :cycleIds
        AND r.status <> com.humano.domain.enumeration.hr.PerformanceReviewStatus.EXCLUDED
        """
    )
    List<ReviewStatRow> findStatRowsByCycleIds(@Param("cycleIds") Collection<UUID> cycleIds);

    /** Review counts of one cycle, per department of the reviewed employee; excluded reviews don't count. */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.ReviewProgressRow(
            d.id, d.name, COUNT(r),
            SUM(CASE WHEN r.selfSubmittedAt IS NOT NULL THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.managerSubmittedAt IS NOT NULL THEN 1 ELSE 0 END),
            SUM(CASE WHEN r.status = :shared THEN 1 ELSE 0 END)
        )
        FROM PerformanceReview r
        JOIN r.employee e
        LEFT JOIN e.department d
        WHERE r.cycle.id = :cycleId
        AND r.status <> com.humano.domain.enumeration.hr.PerformanceReviewStatus.EXCLUDED
        GROUP BY d.id, d.name
        """
    )
    List<ReviewProgressRow> countProgressByDepartment(@Param("cycleId") UUID cycleId, @Param("shared") PerformanceReviewStatus shared);

    /** Whether someone reviews (or reviewed) an employee in a cycle — they may read the employee's goals. */
    boolean existsByEmployeeIdAndReviewerIdAndCycleIsNotNull(UUID employeeId, UUID reviewerId);
}
