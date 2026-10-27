package com.humano.repository.hr;

import com.humano.domain.hr.Training;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Training} entity.
 */
@Repository
public interface TrainingRepository extends JpaRepository<Training, UUID>, JpaSpecificationExecutor<Training> {}
