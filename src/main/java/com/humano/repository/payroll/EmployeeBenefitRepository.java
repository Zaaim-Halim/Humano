package com.humano.repository.payroll;

import com.humano.domain.payroll.EmployeeBenefit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeBenefit} entity.
 */
@Repository
public interface EmployeeBenefitRepository extends JpaRepository<EmployeeBenefit, UUID>, JpaSpecificationExecutor<EmployeeBenefit> {
    boolean existsByEmployeeId(UUID employeeId);
}
