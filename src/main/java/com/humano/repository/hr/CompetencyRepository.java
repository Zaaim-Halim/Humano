package com.humano.repository.hr;

import com.humano.domain.hr.Competency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Competency} entity.
 */
@Repository
public interface CompetencyRepository extends JpaRepository<Competency, UUID> {
    /** The framework, by category then name. */
    List<Competency> findAllByOrderByCategoryAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
