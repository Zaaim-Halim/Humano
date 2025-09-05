package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeTraining;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link EmployeeTraining} entity.
 */
@Repository
public interface EmployeeTrainingRepository extends JpaRepository<EmployeeTraining, UUID> , JpaSpecificationExecutor<EmployeeTraining> {
}
