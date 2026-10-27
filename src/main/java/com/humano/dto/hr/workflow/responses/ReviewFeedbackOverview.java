package com.humano.dto.hr.workflow.responses;

import com.humano.domain.enumeration.hr.FeedbackVisibility;
import java.util.List;

/**
 * The feedback requests of one review, as the viewer may see them, with what they can do.
 *
 * @param anonymous the contributions are shown without names (the reviewed employee, in an
 *                  anonymous cycle)
 * @param withheld anonymous contributions are hidden because fewer than {@code minimumResponses}
 *                 contributors answered
 * @param activeContributors contributors nominated, asked or answered — counted against
 *                           {@code maxContributors}
 * @param canRequest the viewer can ask someone directly (the reviewer or HR)
 * @param canNominate the viewer can nominate someone (the reviewed employee, when the cycle allows it)
 * @param canManage the viewer can approve, decline and cancel requests
 */
public record ReviewFeedbackOverview(
    FeedbackVisibility visibility,
    boolean anonymous,
    boolean withheld,
    int minimumResponses,
    int maxContributors,
    int activeContributors,
    boolean canRequest,
    boolean canNominate,
    boolean canManage,
    List<ReviewFeedbackResponse> requests
) {}
