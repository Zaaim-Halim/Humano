package com.humano.domain.hr;

import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * A question a review template asks — of the employee in their self-assessment, or of the reviewer
 * — and whether an answer is required.
 */
@Embeddable
public class ReviewQuestionDefinition {

    public static final int TEXT_LENGTH = 500;
    public static final int HELP_LENGTH = 1000;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 20)
    private ReviewQuestionAudience audience;

    @Column(name = "question_text", nullable = false, length = TEXT_LENGTH)
    private String text;

    @Column(name = "help_text", length = HELP_LENGTH)
    private String helpText;

    @Column(name = "required", nullable = false)
    private boolean required;

    public ReviewQuestionDefinition() {}

    public ReviewQuestionDefinition(ReviewQuestionAudience audience, String text, String helpText, boolean required) {
        this.audience = audience;
        this.text = text;
        this.helpText = helpText;
        this.required = required;
    }

    /** A copy, for a cycle's snapshot. */
    public ReviewQuestionDefinition copy() {
        return new ReviewQuestionDefinition(audience, text, helpText, required);
    }

    public ReviewQuestionAudience getAudience() {
        return audience;
    }

    public String getText() {
        return text;
    }

    public String getHelpText() {
        return helpText;
    }

    public boolean isRequired() {
        return required;
    }
}
