package com.humano.repository.payroll;

import com.humano.domain.payroll.Payslip;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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
public interface PayslipRepository extends JpaRepository<Payslip, UUID>, JpaSpecificationExecutor<Payslip> {
    /** List pages fetch result, employee (department, position) and period in the same query. */
    @Override
    @EntityGraph(
        attributePaths = {
            "result",
            "result.employee",
            "result.employee.department",
            "result.employee.position",
            "result.payrollPeriod",
            "result.currency",
        }
    )
    Page<Payslip> findAll(Specification<Payslip> spec, Pageable pageable);
}
