package com.humano.repository.payroll;

import com.humano.domain.payroll.Payslip;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Payslip} entity.
 * <p>
 * {@code Payslip} only references {@code PayrollResult} directly; employee scoping is done
 * via {@link JpaSpecificationExecutor} in {@code PayslipService}.
 */
@Repository
public interface PayslipRepository extends JpaRepository<Payslip, UUID>, JpaSpecificationExecutor<Payslip> {}
