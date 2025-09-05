package com.humano.repository.hr;

import com.humano.domain.hr.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link LeaveRequest} entity.
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> , JpaSpecificationExecutor<LeaveRequest> {
}
