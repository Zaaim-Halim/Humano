package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.ExpenseClaimStatus;
import com.humano.domain.hr.ExpenseClaim;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ExpenseClaim} entity.
 */
@Repository
public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, UUID>, JpaSpecificationExecutor<ExpenseClaim> {
    Page<ExpenseClaim> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<ExpenseClaim> findByStatus(ExpenseClaimStatus status, Pageable pageable);

    Page<ExpenseClaim> findByEmployeeIdAndClaimDateBetween(UUID employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);
}
