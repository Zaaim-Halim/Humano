package com.humano.web.rest.platform;

import com.humano.dto.platform.PlatformOverview;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.platform.PlatformMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The platform owner's KPIs and reports (customers, revenue, collections, plan mix). */
@RestController
@RequestMapping("/api/platform/metrics")
@RequirePermission(PermissionsConstants.VIEW_PLATFORM_METRICS)
public class PlatformMetricsResource {

    private final PlatformMetricsService metricsService;

    public PlatformMetricsResource(PlatformMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<PlatformOverview> overview() {
        return ResponseEntity.ok(metricsService.overview());
    }
}
