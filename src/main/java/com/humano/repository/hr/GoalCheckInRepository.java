package com.humano.repository.hr;

import com.humano.domain.hr.GoalCheckIn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link GoalCheckIn} entity.
 */
@Repository
public interface GoalCheckInRepository extends JpaRepository<GoalCheckIn, UUID> {
    /** A goal's check-ins, newest first. */
    @EntityGraph(attributePaths = { "author" })
    List<GoalCheckIn> findByGoalIdOrderByCheckedInAtDescIdDesc(UUID goalId);
}
