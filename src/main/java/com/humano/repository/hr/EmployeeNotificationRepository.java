package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link EmployeeNotification} entity.
 */
@Repository
public interface EmployeeNotificationRepository extends JpaRepository<EmployeeNotification, UUID> {
}
