package com.humano.repository.hr;

import com.humano.domain.hr.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link ExpenseClaim} entity.
 */
@Repository
public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID> , JpaSpecificationExecutor<ExpenseClaim> {
}
