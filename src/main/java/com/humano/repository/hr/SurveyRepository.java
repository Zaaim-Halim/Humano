package com.humano.repository.hr;

import com.humano.domain.hr.Survey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Survey} entity.
 */
@Repository
public interface SurveyRepository extends JpaRepository<Survey, UUID> {
}
