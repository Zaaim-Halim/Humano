package com.humano.repository.hr;

import com.humano.domain.hr.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link SurveyResponse} entity.
 */
@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {
}
