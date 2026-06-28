package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeExperience;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeExperience} entity.
 */
@Repository
public interface EmployeeExperienceRepository extends JpaRepository<EmployeeExperience, UUID> {
    List<EmployeeExperience> findByEmployeeId(UUID employeeId);
}
