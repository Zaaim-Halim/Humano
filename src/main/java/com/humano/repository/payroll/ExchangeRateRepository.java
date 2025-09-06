package com.humano.repository.payroll;

import com.humano.domain.payroll.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link ExchangeRate} entity.
 */
@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID>, JpaSpecificationExecutor<ExchangeRate> {
}
