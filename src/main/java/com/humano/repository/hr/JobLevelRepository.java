package com.humano.repository.hr;

import com.humano.domain.hr.JobLevel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link JobLevel} reference entity.
 */
@Repository
public interface JobLevelRepository extends JpaRepository<JobLevel, UUID> {
    Optional<JobLevel> findByCode(String code);
}
