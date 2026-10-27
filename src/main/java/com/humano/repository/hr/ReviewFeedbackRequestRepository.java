package com.humano.repository.hr;

import com.humano.domain.enumeration.hr.FeedbackRequestStatus;
import com.humano.domain.hr.ReviewFeedbackRequest;
import com.humano.repository.hr.projection.FeedbackCountRow;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewFeedbackRequest} entity. Reads mapped to a
 * response fetch the contributor, who asked, and the review with its employee and cycle.
 */
@Repository
public interface ReviewFeedbackRequestRepository extends JpaRepository<ReviewFeedbackRequest, UUID> {
    @EntityGraph(attributePaths = { "contributor", "requestedBy", "review", "review.employee", "review.reviewer", "review.cycle" })
    Optional<ReviewFeedbackRequest> findWithReviewById(UUID id);

    /** The requests of one review, oldest first. */
    @EntityGraph(attributePaths = { "contributor", "requestedBy" })
    List<ReviewFeedbackRequest> findByReviewIdOrderByRequestedAtAscIdAsc(UUID reviewId);

    @EntityGraph(attributePaths = { "contributor", "review", "review.employee", "review.reviewer", "review.cycle" })
    List<ReviewFeedbackRequest> findByReviewIdAndStatusIn(UUID reviewId, Collection<FeedbackRequestStatus> statuses);

    @EntityGraph(attributePaths = { "contributor", "review", "review.employee", "review.reviewer", "review.cycle" })
    List<ReviewFeedbackRequest> findByReviewCycleIdAndStatusIn(UUID cycleId, Collection<FeedbackRequestStatus> statuses);

    /** The contributor's requests in cycles that are still running, soonest due first. */
    @EntityGraph(attributePaths = { "contributor", "requestedBy", "review", "review.employee", "review.reviewer", "review.cycle" })
    List<ReviewFeedbackRequest> findByContributorIdAndStatusInAndReviewCycleActiveTrueOrderByDueDateAscRequestedAtAsc(
        UUID contributorId,
        Collection<FeedbackRequestStatus> statuses
    );

    boolean existsByReviewIdAndContributorIdAndStatusIn(UUID reviewId, UUID contributorId, Collection<FeedbackRequestStatus> statuses);

    long countByReviewIdAndStatusIn(UUID reviewId, Collection<FeedbackRequestStatus> statuses);

    /** Request counts per review and status, for one cycle. */
    @Query(
        """
        SELECT new com.humano.repository.hr.projection.FeedbackCountRow(f.review.id, f.status, COUNT(f))
        FROM ReviewFeedbackRequest f
        WHERE f.review.cycle.id = :cycleId
        GROUP BY f.review.id, f.status
        """
    )
    List<FeedbackCountRow> countByCycle(@Param("cycleId") UUID cycleId);
}
