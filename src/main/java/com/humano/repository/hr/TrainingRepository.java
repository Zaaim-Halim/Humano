package com.humano.repository.hr;

import com.humano.domain.hr.Training;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Training} entity.
 */
@Repository
public interface TrainingRepository extends JpaRepository<Training, UUID> , JpaSpecificationExecutor<Training> {
}
