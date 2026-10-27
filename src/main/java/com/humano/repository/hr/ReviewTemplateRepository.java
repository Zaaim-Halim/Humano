package com.humano.repository.hr;

import com.humano.domain.hr.ReviewTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewTemplate} entity.
 */
@Repository
public interface ReviewTemplateRepository extends JpaRepository<ReviewTemplate, UUID> {
    List<ReviewTemplate> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /** The names of the templates using a competency. */
    @Query("select t.name from ReviewTemplate t join t.competencies c where c.id = :competencyId order by t.name")
    List<String> findNamesUsingCompetency(@Param("competencyId") UUID competencyId);
}
