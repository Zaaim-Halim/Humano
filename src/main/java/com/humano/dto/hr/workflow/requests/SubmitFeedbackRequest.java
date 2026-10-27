package com.humano.dto.hr.workflow.requests;

import jakarta.validation.constraints.Size;

/**
 * A contributor's feedback on a colleague's review. Narrative only, like the self-assessment;
 * strengths or improvements are needed.
 */
public record SubmitFeedbackRequest(
    @Size(max = 10000) String strengths,

    @Size(max = 10000) String improvements,

    @Size(max = 10000) String comments
) {}
