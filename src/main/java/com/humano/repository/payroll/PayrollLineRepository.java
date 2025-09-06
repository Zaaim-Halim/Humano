package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayrollLine} entity.
 */
@Repository
public interface PayrollLineRepository extends JpaRepository<PayrollLine, UUID>, JpaSpecificationExecutor<PayrollLine> {
}
