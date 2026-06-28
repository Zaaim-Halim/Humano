package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeLicense;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeLicense} entity.
 */
@Repository
public interface EmployeeLicenseRepository extends JpaRepository<EmployeeLicense, UUID> {
    List<EmployeeLicense> findByEmployeeId(UUID employeeId);
}
