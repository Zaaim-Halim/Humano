package com.humano.repository.hr;

import com.humano.domain.hr.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link Timesheet} entity.
 */
@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> , JpaSpecificationExecutor<Timesheet> {
}
