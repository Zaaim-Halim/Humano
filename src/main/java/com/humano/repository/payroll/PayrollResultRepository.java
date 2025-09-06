package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayrollResult} entity.
 */
@Repository
public interface PayrollResultRepository extends JpaRepository<PayrollResult, UUID>, JpaSpecificationExecutor<PayrollResult> {
}
