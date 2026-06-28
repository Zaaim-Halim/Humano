package com.humano.repository.hr;

import com.humano.domain.hr.JobGrade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link JobGrade} reference entity.
 */
@Repository
public interface JobGradeRepository extends JpaRepository<JobGrade, UUID> {
    Optional<JobGrade> findByCode(String code);
}
