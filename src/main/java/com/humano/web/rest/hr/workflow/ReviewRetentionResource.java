package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.PurgeReviewCyclesRequest;
import com.humano.dto.hr.workflow.requests.ReviewRetentionSettingsRequest;
import com.humano.dto.hr.workflow.responses.ReviewRetentionPurgeResult;
import com.humano.dto.hr.workflow.responses.ReviewRetentionResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.ReviewRetentionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the retention of performance review records
 * ({@code /api/hr/workflows/review-retention}), all with {@code CONFIGURE_PERFORMANCE_CYCLES}: how
 * long they are kept, which cycles are due for deletion or held back, and deleting the due ones.
 * <p>
 * Only deleting from here needs the performance feature. The settings stay readable and changeable
 * without it, because the nightly job keeps applying them to a tenant that lost the feature, so
 * that tenant must still be able to turn automatic deletion off or lengthen the period.
 */
@RequirePermission(PermissionsConstants.CONFIGURE_PERFORMANCE_CYCLES)
@RestController
@RequestMapping("/api/hr/workflows/review-retention")
public class ReviewRetentionResource {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewRetentionResource.class);

    private final ReviewRetentionService service;

    public ReviewRetentionResource(ReviewRetentionService service) {
        this.service = service;
    }

    /** {@code GET /} : the settings (defaults until saved) and the cycles due and held today. */
    @GetMapping
    public ResponseEntity<ReviewRetentionResponse> get() {
        LOG.debug("REST request to get review retention");
        return ResponseEntity.ok(service.get());
    }

    /** {@code PUT /} : replace the settings; deletes nothing. */
    @PutMapping
    public ResponseEntity<ReviewRetentionResponse> update(@Valid @RequestBody ReviewRetentionSettingsRequest request) {
        LOG.debug("REST request to update review retention: {}", request);
        return ResponseEntity.ok(service.save(request));
    }

    /** {@code POST /purge} : delete the cycles due — exactly the ones listed. */
    @PostMapping("/purge")
    @RequireFeature(FeatureCodes.PERFORMANCE)
    public ResponseEntity<ReviewRetentionPurgeResult> purge(@Valid @RequestBody PurgeReviewCyclesRequest request) {
        LOG.debug("REST request to delete {} review cycle(s) under the retention period", request.cycleIds().size());
        return ResponseEntity.ok(service.purge(request));
    }
}
