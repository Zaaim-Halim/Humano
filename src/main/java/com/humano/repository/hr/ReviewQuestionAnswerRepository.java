package com.humano.repository.hr;

import com.humano.domain.hr.ReviewQuestionAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ReviewQuestionAnswer} entity.
 */
@Repository
public interface ReviewQuestionAnswerRepository extends JpaRepository<ReviewQuestionAnswer, UUID> {
    @EntityGraph(attributePaths = { "question" })
    List<ReviewQuestionAnswer> findByReviewId(UUID reviewId);
}
