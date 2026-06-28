package com.humano.repository.hr;

import com.humano.domain.hr.EmploymentContract;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmploymentContract} entity.
 */
@Repository
public interface EmploymentContractRepository extends JpaRepository<EmploymentContract, UUID> {
    List<EmploymentContract> findByEmployeeId(UUID employeeId);
}
