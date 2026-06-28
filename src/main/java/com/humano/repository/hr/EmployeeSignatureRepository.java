package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeSignature;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeSignature} entity.
 */
@Repository
public interface EmployeeSignatureRepository extends JpaRepository<EmployeeSignature, UUID> {
    Optional<EmployeeSignature> findByEmployeeId(UUID employeeId);
}
