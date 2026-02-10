package com.humano.repository.hr;

import com.humano.domain.hr.Survey;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Survey} entity.
 */
@Repository
public interface SurveyRepository extends JpaRepository<Survey, UUID> {
    Page<Survey> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate startDate, LocalDate endDate, Pageable pageable);
}
