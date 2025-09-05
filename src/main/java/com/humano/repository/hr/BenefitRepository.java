package com.humano.repository.hr;

import com.humano.domain.hr.Benefit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Benefit} entity.
 */
@Repository
public interface BenefitRepository extends JpaRepository<Benefit, UUID>, JpaSpecificationExecutor<Benefit> {
}
