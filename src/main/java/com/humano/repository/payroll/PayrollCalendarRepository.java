package com.humano.repository.payroll;

import com.humano.domain.payroll.PayrollCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PayrollCalendar} entity.
 */
@Repository
public interface PayrollCalendarRepository extends JpaRepository<PayrollCalendar, UUID>, JpaSpecificationExecutor<PayrollCalendar> {
}
