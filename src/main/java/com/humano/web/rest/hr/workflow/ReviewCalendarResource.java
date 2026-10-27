package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.UpdateReviewCalendarRequest;
import com.humano.dto.hr.workflow.responses.ReviewCalendarResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.ReviewCalendarService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the company's review calendar ({@code /api/hr/workflows/review-calendar}):
 * when review periods run, how long each phase lasts, and the next cycle it proposes. Read by anyone
 * who can see review cycles (the new-cycle form is pre-filled from it); replaced with
 * {@code CONFIGURE_PERFORMANCE_CYCLES}.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RestController
@RequestMapping("/api/hr/workflows/review-calendar")
public class ReviewCalendarResource {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewCalendarResource.class);

    private final ReviewCalendarService service;

    public ReviewCalendarResource(ReviewCalendarService service) {
        this.service = service;
    }

    /** {@code GET /} : the calendar (defaults until saved) and the next proposed cycle. */
    @GetMapping
    @PreAuthorize(
        "@securityExpressions.hasAnyPermission('" +
        PermissionsConstants.VIEW_PERFORMANCE_REVIEWS +
        "', '" +
        PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES +
        "')"
    )
    public ResponseEntity<ReviewCalendarResponse> get() {
        LOG.debug("REST request to get the review calendar");
        return ResponseEntity.ok(service.get());
    }

    /** {@code PUT /} : replace the calendar. */
    @PutMapping
    @RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
    public ResponseEntity<ReviewCalendarResponse> update(@Valid @RequestBody UpdateReviewCalendarRequest request) {
        LOG.debug("REST request to update the review calendar: {}", request);
        return ResponseEntity.ok(service.update(request));
    }
}
