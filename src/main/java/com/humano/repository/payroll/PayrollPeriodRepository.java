package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayrollPeriod} entity.
 */
@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID>, JpaSpecificationExecutor<PayrollPeriod> {
}
