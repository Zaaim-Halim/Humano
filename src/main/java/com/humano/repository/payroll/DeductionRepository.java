package com.humano.repository.payroll;

import com.humano.domain.payroll.Deduction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

/**
 * Spring Data JPA repository for the {@link Deduction} entity.
 */
@Repository
public interface DeductionRepository extends JpaRepository<Deduction, UUID>, JpaSpecificationExecutor<Deduction> {
    List<Deduction> findByEmployeeId(UUID employeeId);
}
