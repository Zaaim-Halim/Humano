package com.humano.web.rest.hr.workflow;

import com.humano.domain.enumeration.hr.ReviewCycleType;
import com.humano.dto.hr.workflow.responses.PerformanceAnalyticsResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequireFeature;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.billing.entitlement.FeatureCodes;
import com.humano.service.hr.workflow.PerformanceAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for performance analytics across cycles, for HR with {@code VIEW_PERFORMANCE_REVIEWS}.
 */
@RequireFeature(FeatureCodes.PERFORMANCE)
@RequirePermission(PermissionsConstants.VIEW_PERFORMANCE_REVIEWS)
@RestController
@RequestMapping("/api/hr/performance-analytics")
public class PerformanceAnalyticsResource {

    private final PerformanceAnalyticsService analyticsService;

    public PerformanceAnalyticsResource(PerformanceAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /** {@code GET  /?type=REGULAR&limit=6} : The latest cycles of a type, newest first (at most 12). */
    @GetMapping
    public ResponseEntity<PerformanceAnalyticsResponse> analytics(
        @RequestParam(defaultValue = "REGULAR") ReviewCycleType type,
        @RequestParam(defaultValue = "6") int limit
    ) {
        return ResponseEntity.ok(analyticsService.analytics(type, limit));
    }
}
