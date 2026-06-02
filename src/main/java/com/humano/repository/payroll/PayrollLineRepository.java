package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PayrollLine} entity.
 * <p>
 * Filtering by {@code PayrollResult} happens via {@link JpaSpecificationExecutor} in
 * {@code PayrollProcessingService}; no direct derived lookups are needed.
 */
@Repository
public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID>, JpaSpecificationExecutor<PayrollLine> {}
