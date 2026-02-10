package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.HealthInsuranceStatus;
import com.humano.domain.hr.HealthInsurance;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link HealthInsurance} entity.
 */
@Repository
public interface HealthInsuranceRepository extends JpaRepository<HealthInsurance, UUID> {
    Page<HealthInsurance> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<HealthInsurance> findByStatus(HealthInsuranceStatus status, Pageable pageable);
}
