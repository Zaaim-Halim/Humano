package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.CompetencyCategory;
import com.humano.domain.enumeration.hr.GoalAchievement;
import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a review asks beyond the standard form — its cycle's template as copied when the cycle opened,
 * and the employee's goals for the period — with the answers given so far. For the reviewed employee, the reviewer's answers and competency
 * ratings are left out until the review is shared.
 */
public record ReviewForm(
    String templateName,
    List<FormCompetency> competencies,
    List<FormQuestion> questions,
    List<Answer> answers,
    List<CompetencyRating> ratings,
    List<FormGoal> goals
) {
    /** {@code anchors}: what rating levels 1 to 5 look like (blank levels are {@code null}). */
    public record FormCompetency(UUID id, String name, String description, CompetencyCategory category, List<String> anchors) {}

    public record FormQuestion(UUID id, ReviewQuestionAudience audience, String text, String helpText, boolean required) {}

    public record Answer(UUID questionId, String answer) {}

    public record CompetencyRating(UUID competencyId, Integer rating, String comment) {}

    /**
     * A goal the review rates: due within the review period, or rated when the review was submitted
     * (then with the title and weight it had). {@code achievement} and {@code comment} are the
     * reviewer's, left out for the employee until the review is shared.
     */
    public record FormGoal(
        UUID id,
        String title,
        String successMeasure,
        Integer weight,
        LocalDate dueDate,
        GoalStatus status,
        int progress,
        GoalAchievement achievement,
        String comment
    ) {}
}
