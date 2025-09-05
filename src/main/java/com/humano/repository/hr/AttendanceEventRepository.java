package com.humano.repository.hr;

import com.humano.domain.hr.AttendanceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link AttendanceEvent} entity.
 */
@Repository
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID>, JpaSpecificationExecutor<AttendanceEvent> {
}
