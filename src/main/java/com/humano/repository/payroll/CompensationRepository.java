package com.humano.repository.payroll;

import com.humano.domain.payroll.Compensation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Compensation} entity.
 */
@Repository
public interface CompensationRepository extends JpaRepository<Compensation, UUID>, JpaSpecificationExecutor<Compensation> {
}
