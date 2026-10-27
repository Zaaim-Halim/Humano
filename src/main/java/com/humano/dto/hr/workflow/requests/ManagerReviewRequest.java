package com.humano.dto.hr.workflow.requests;

import com.humano.domain.enumeration.hr.ProbationRecommendation;
import com.humano.domain.enumeration.hr.ReviewPotential;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for submitting a manager review of {@code employeeId} in a cycle. {@code potential}
 * is optional; {@code probationRecommendation} is required in a probation cycle and ignored
 * otherwise. Neither is ever shown to the employee.
 */
public record ManagerReviewRequest(
    @NotNull(message = "Employee ID is required") UUID employeeId,

    @NotNull(message = "Rating is required") @Min(1) @Max(5) Integer rating,

    @Size(max = 10000) String strengths,

    @Size(max = 10000) String areasForImprovement,

    @Size(max = 10000) String managerComments,

    @Size(max = 10000) String developmentRecommendations,

    boolean recommendForPromotion,

    @Size(max = 10000) String promotionJustification,

    ReviewPotential potential,

    ProbationRecommendation probationRecommendation,

    @Valid @Size(max = 20) List<@NotNull CompetencyRatingInput> competencyRatings,

    @Valid @Size(max = 20) List<@NotNull ReviewAnswerInput> answers,

    @Valid @Size(max = 50) List<@NotNull GoalRatingInput> goalRatings
) {
    public ManagerReviewRequest(
        UUID employeeId,
        Integer rating,
        String strengths,
        String areasForImprovement,
        String managerComments,
        String developmentRecommendations,
        boolean recommendForPromotion,
        String promotionJustification,
        ReviewPotential potential,
        ProbationRecommendation probationRecommendation,
        List<CompetencyRatingInput> competencyRatings,
        List<ReviewAnswerInput> answers
    ) {
        this(
            employeeId,
            rating,
            strengths,
            areasForImprovement,
            managerComments,
            developmentRecommendations,
            recommendForPromotion,
            promotionJustification,
            potential,
            probationRecommendation,
            competencyRatings,
            answers,
            null
        );
    }

    public ManagerReviewRequest(
        UUID employeeId,
        Integer rating,
        String strengths,
        String areasForImprovement,
        String managerComments,
        String developmentRecommendations,
        boolean recommendForPromotion,
        String promotionJustification,
        ReviewPotential potential,
        ProbationRecommendation probationRecommendation
    ) {
        this(
            employeeId,
            rating,
            strengths,
            areasForImprovement,
            managerComments,
            developmentRecommendations,
            recommendForPromotion,
            promotionJustification,
            potential,
            probationRecommendation,
            null,
            null
        );
    }

    public ManagerReviewRequest(
        UUID employeeId,
        Integer rating,
        String strengths,
        String areasForImprovement,
        String managerComments,
        String developmentRecommendations,
        boolean recommendForPromotion,
        String promotionJustification
    ) {
        this(
            employeeId,
            rating,
            strengths,
            areasForImprovement,
            managerComments,
            developmentRecommendations,
            recommendForPromotion,
            promotionJustification,
            null,
            null,
            null,
            null
        );
    }
}
