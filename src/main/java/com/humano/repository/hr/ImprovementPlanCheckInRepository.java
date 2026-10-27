package com.humano.repository.hr;

import com.humano.domain.hr.ImprovementPlanCheckIn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ImprovementPlanCheckIn} entity.
 */
@Repository
public interface ImprovementPlanCheckInRepository extends JpaRepository<ImprovementPlanCheckIn, UUID> {
    /** A plan's check-ins, newest first. */
    @EntityGraph(attributePaths = "author")
    List<ImprovementPlanCheckIn> findByPlanIdOrderByCheckedInAtDescIdDesc(UUID planId);
}
