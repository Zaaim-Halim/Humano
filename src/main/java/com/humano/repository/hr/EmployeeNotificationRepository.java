package com.humano.repository.hr;

import com.humano.domain.hr.EmployeeNotification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link EmployeeNotification} entity.
 */
@Repository
public interface EmployeeNotificationRepository extends JpaRepository<EmployeeNotification, UUID> {}
