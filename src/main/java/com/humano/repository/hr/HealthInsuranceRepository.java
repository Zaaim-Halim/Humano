package com.humano.repository.hr;

import com.humano.domain.hr.HealthInsurance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link HealthInsurance} entity.
 */
@Repository
public interface HealthInsuranceRepository extends JpaRepository<HealthInsurance, UUID> {
}
