package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.EmployeeStatus;
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.hr.Goal;
import com.humano.repository.hr.projection.TeamMemberRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Goal} entity.
 */
@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {
    /** Someone's goals, soonest due first. */
    @EntityGraph(attributePaths = { "employee", "agreedBy" })
    List<Goal> findByEmployeeIdOrderByDueDateAscCreatedDateAsc(UUID employeeId);

    /** The goals of a manager's direct reports, soonest due first. */
    @EntityGraph(attributePaths = { "employee", "agreedBy" })
    List<Goal> findByEmployeeManagerIdOrderByDueDateAscCreatedDateAsc(UUID managerId);

    /** Someone's goals in some statuses due within a period — a review's goals. */
    List<Goal> findByEmployeeIdAndStatusInAndDueDateBetweenOrderByDueDateAscTitleAsc(
        UUID employeeId,
        Collection<GoalStatus> statuses,
        LocalDate from,
        LocalDate to
    );

    /** A manager's direct reports still working here — whose goals they set and agree. */
    @Query(
        "select new com.humano.repository.hr.projection.TeamMemberRow(e.id, trim(concat(coalesce(e.firstName, ''), ' ', coalesce(e.lastName, '')))) " +
        "from Employee e where e.manager.id = :managerId and e.status in :statuses order by e.firstName, e.lastName"
    )
    List<TeamMemberRow> findTeamMembers(@Param("managerId") UUID managerId, @Param("statuses") Collection<EmployeeStatus> statuses);
}
