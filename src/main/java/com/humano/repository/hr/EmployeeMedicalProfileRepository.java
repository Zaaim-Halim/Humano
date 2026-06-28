package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeMedicalProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeMedicalProfile} entity.
 */
@Repository
public interface EmployeeMedicalProfileRepository extends JpaRepository<EmployeeMedicalProfile, UUID> {
    Optional<EmployeeMedicalProfile> findByEmployeeId(UUID employeeId);
}
