package com.humano.repository.payroll;

import com.humano.domain.payroll.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Payslip} entity.
 */
@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID>, JpaSpecificationExecutor<Payslip> {
}
