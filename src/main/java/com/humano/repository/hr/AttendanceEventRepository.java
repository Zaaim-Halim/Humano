package com.humano.repository.hr;

import com.humano.domain.hr.AttendanceEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AttendanceEvent} entity.
 */
@Repository
public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID>, JpaSpecificationExecutor<AttendanceEvent> {}
