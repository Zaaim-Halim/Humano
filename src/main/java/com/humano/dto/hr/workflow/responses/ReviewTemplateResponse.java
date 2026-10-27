package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import java.util.List;
import java.util.UUID;

/**
 * A review template: its competencies and questions in order, and how many cycles use it (a
 * template in use can be retired but not deleted).
 */
public record ReviewTemplateResponse(
    UUID id,
    String name,
    String description,
    boolean active,
    List<CompetencyResponse> competencies,
    List<Question> questions,
    long cycles
) {
    public record Question(ReviewQuestionAudience audience, String text, String helpText, boolean required) {}
}
