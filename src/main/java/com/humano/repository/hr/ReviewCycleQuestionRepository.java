package com.humano.repository.hr;

import com.humano.domain.hr.ReviewCycleQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewCycleQuestion} entity: a cycle's copy of its
 * template's questions.
 */
@Repository
public interface ReviewCycleQuestionRepository extends JpaRepository<ReviewCycleQuestion, UUID> {
    List<ReviewCycleQuestion> findByCycleIdOrderBySortOrderAsc(UUID cycleId);

    boolean existsByCycleId(UUID cycleId);
}
