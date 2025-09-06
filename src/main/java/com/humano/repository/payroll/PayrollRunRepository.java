package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayrollRun} entity.
 */
@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, UUID>, JpaSpecificationExecutor<PayrollRun> {
}
