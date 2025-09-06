package com.humano.repository.payroll;

import com.humano.domain.payroll.PayRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayRule} entity.
 */
@Repository
public interface PayRuleRepository extends JpaRepository<PayRule, UUID>, JpaSpecificationExecutor<PayRule> {
}
