package com.humano.repository.hr;

import com.humano.domain.hr.ReviewCycleCompetency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewCycleCompetency} entity: a cycle's copy of its
 * template's competencies.
 */
@Repository
public interface ReviewCycleCompetencyRepository extends JpaRepository<ReviewCycleCompetency, UUID> {
    List<ReviewCycleCompetency> findByCycleIdOrderBySortOrderAsc(UUID cycleId);

    boolean existsByCycleId(UUID cycleId);
}
