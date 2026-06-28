package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeEducation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeEducation} entity.
 */
@Repository
public interface EmployeeEducationRepository extends JpaRepository<EmployeeEducation, UUID> {
    List<EmployeeEducation> findByEmployeeId(UUID employeeId);
}
