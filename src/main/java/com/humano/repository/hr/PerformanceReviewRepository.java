package com.humano.repository.hr;

import com.humano.domain.hr.PerformanceReview;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link PerformanceReview} entity.
 */
@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {
    Page<PerformanceReview> findByEmployeeId(UUID employeeId, Pageable pageable);

    Page<PerformanceReview> findByReviewerId(UUID reviewerId, Pageable pageable);
}
