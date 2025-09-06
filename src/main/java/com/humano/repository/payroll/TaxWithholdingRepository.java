package com.humano.repository.payroll;

import com.humano.domain.payroll.TaxWithholding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link TaxWithholding} entity.
 */
@Repository
public interface TaxWithholdingRepository extends JpaRepository<TaxWithholding, UUID>, JpaSpecificationExecutor<TaxWithholding> {
}
