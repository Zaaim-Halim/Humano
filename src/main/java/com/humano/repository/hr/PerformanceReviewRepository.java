package com.humano.repository.hr;

import com.humano.domain.hr.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for the {@link PerformanceReview} entity.
 */
@Repository
public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, UUID> {
}
