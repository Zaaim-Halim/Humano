package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.ImprovementPlanStatus;
import com.humano.domain.hr.ImprovementPlan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ImprovementPlan} entity.
 */
@Repository
public interface ImprovementPlanRepository extends JpaRepository<ImprovementPlan, UUID> {
    /** Whether someone has a plan in one of these statuses — at most one is open at a time. */
    boolean existsByEmployeeIdAndStatusIn(UUID employeeId, Collection<ImprovementPlanStatus> statuses);

    /** A plan with what its pages show. */
    @EntityGraph(attributePaths = { "employee", "employee.manager", "owner", "review", "review.cycle", "closedBy" })
    Optional<ImprovementPlan> findWithDetailsById(UUID id);

    /** Someone's plans in some statuses, newest first. */
    @EntityGraph(attributePaths = { "employee", "employee.manager", "owner" })
    List<ImprovementPlan> findByEmployeeIdAndStatusInOrderByCreatedDateDesc(UUID employeeId, Collection<ImprovementPlanStatus> statuses);

    /** Plans a manager runs: of their direct reports, or started by them. */
    @EntityGraph(attributePaths = { "employee", "employee.manager", "owner" })
    @Query(
        "select p from ImprovementPlan p join p.employee e left join e.manager m left join p.owner o " +
        "where e.id <> :managerId and (m.id = :managerId or o.id = :managerId) order by p.createdDate desc"
    )
    List<ImprovementPlan> findManagedBy(@Param("managerId") UUID managerId);

    /** Every plan, newest first — for HR. */
    @EntityGraph(attributePaths = { "employee", "employee.manager", "owner" })
    List<ImprovementPlan> findAllByOrderByCreatedDateDesc();

    /** The plans started from a review, newest first. */
    List<ImprovementPlan> findByReviewIdOrderByCreatedDateDesc(UUID reviewId);

    /** The reviews of some cycles that an improvement plan was started from. */
    @Query("select distinct p.review.id from ImprovementPlan p where p.review.cycle.id in :cycleIds")
    List<UUID> findReviewIdsByCycleIds(@Param("cycleIds") Collection<UUID> cycleIds);

    /** Plans following a cycle's reviews in some statuses. */
    long countByReviewCycleIdAndStatusIn(UUID cycleId, Collection<ImprovementPlanStatus> statuses);
}
