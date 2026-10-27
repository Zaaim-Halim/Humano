package com.humano.repository.hr;

import com.humano.domain.hr.ReviewRatingChange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewRatingChange} entity.
 */
@Repository
public interface ReviewRatingChangeRepository extends JpaRepository<ReviewRatingChange, UUID> {
    /** A review's rating changes, oldest first. */
    @EntityGraph(attributePaths = { "changedBy" })
    List<ReviewRatingChange> findByReviewIdOrderByChangedAtAscIdAsc(UUID reviewId);
}
