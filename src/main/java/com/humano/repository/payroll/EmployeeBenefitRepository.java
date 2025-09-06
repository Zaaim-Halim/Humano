package com.humano.repository.payroll;

import com.humano.domain.payroll.EmployeeBenefit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link EmployeeBenefit} entity.
 */
@Repository
public interface EmployeeBenefitRepository extends JpaRepository<EmployeeBenefit, UUID>, JpaSpecificationExecutor<EmployeeBenefit> {
}
