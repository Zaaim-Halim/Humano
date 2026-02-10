package com.humano.repository.hr;

import com.humano.domain.hr.SurveyResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link SurveyResponse} entity.
 */
@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {
    Page<SurveyResponse> findBySurveyId(UUID surveyId, Pageable pageable);
}
