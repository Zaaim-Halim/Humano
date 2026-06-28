package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeBankAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeBankAccount} entity.
 */
@Repository
public interface EmployeeBankAccountRepository extends JpaRepository<EmployeeBankAccount, UUID> {
    List<EmployeeBankAccount> findByEmployeeId(UUID employeeId);
}
