package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayrollInput} entity.
 */
@Repository
public interface PayrollInputRepository extends JpaRepository<PayrollInput, UUID>, JpaSpecificationExecutor<PayrollInput> {
}
