package com.humano.repository.hr;

import com.humano.domain.hr.ReviewCompetencyRating;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewCompetencyRating} entity.
 */
@Repository
public interface ReviewCompetencyRatingRepository extends JpaRepository<ReviewCompetencyRating, UUID> {
    List<ReviewCompetencyRating> findByReviewId(UUID reviewId);
}
