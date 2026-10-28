package com.humano.repository.tenant;

import com.humano.domain.enumeration.tenant.DemoRequestStatus;
import com.humano.domain.tenant.DemoRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DemoRequestRepository extends JpaRepository<DemoRequest, UUID> {
    @EntityGraph(attributePaths = "tenant")
    Page<DemoRequest> findAllByOrderByCreatedDateDesc(Pageable pageable);

    @EntityGraph(attributePaths = "tenant")
    Page<DemoRequest> findByStatusOrderByCreatedDateDesc(DemoRequestStatus status, Pageable pageable);

    long countByStatus(DemoRequestStatus status);

    /** Same address, recently: a double submit or a bot. */
    boolean existsByWorkEmailIgnoreCaseAndCreatedDateAfter(String workEmail, Instant after);

    /** Requests last touched before {@code before} (or never touched and created before it). */
    @EntityGraph(attributePaths = "tenant")
    @Query("SELECT r FROM DemoRequest r WHERE COALESCE(r.lastModifiedDate, r.createdDate) < :before")
    List<DemoRequest> findUntouchedSince(@Param("before") Instant before);
}
