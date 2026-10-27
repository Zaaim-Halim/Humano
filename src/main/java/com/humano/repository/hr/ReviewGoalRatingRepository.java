package com.humano.repository.hr;

import com.humano.domain.hr.ReviewGoalRating;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewGoalRating} entity.
 */
@Repository
public interface ReviewGoalRatingRepository extends JpaRepository<ReviewGoalRating, UUID> {
    @EntityGraph(attributePaths = { "goal" })
    List<ReviewGoalRating> findByReviewId(UUID reviewId);

    /** Whether a goal was rated in a submitted review (ratings are only written on submission). */
    boolean existsByGoalId(UUID goalId);

    /** Which of these goals were rated in a submitted review. */
    @Query("select distinct r.goal.id from ReviewGoalRating r where r.goal.id in :goalIds")
    Set<UUID> findRatedGoalIds(@Param("goalIds") Collection<UUID> goalIds);
}
