package com.humano.web.rest.platform;

import com.humano.dto.platform.compliance.RetentionChangeRequest;
import com.humano.dto.platform.compliance.RetentionSettings;
import com.humano.dto.platform.compliance.ScheduledDeletions;
import com.humano.dto.platform.compliance.SubprocessorNoticeRequest;
import com.humano.dto.platform.compliance.SubprocessorNoticeResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.platform.ComplianceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The platform console's data-protection page: workspaces scheduled for deletion under the retention
 * period and the period itself, and sub-processor notices. Reading needs {@code VIEW_PLATFORM_TENANTS};
 * changing the period (which the Terms quote) and sending a notice (which emails every customer) are for the
 * owner ({@code MANAGE_PLATFORM_BILLING}, who manages the customer contract).
 */
@RestController
@RequestMapping("/api/platform/compliance")
public class PlatformComplianceResource {

    private final ComplianceService complianceService;

    public PlatformComplianceResource(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @GetMapping("/deletions")
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_TENANTS)
    public ScheduledDeletions deletions() {
        return complianceService.scheduledDeletions();
    }

    @GetMapping("/retention")
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_TENANTS)
    public RetentionSettings retention() {
        return complianceService.retentionSettings();
    }

    /** Sets the retention period: longer applies at once, shorter from a date at least 30 days ahead. */
    @PutMapping("/retention")
    @RequirePermission(PermissionsConstants.MANAGE_PLATFORM_BILLING)
    public RetentionSettings changeRetention(@Valid @RequestBody RetentionChangeRequest request) {
        return complianceService.changeRetention(request);
    }

    /** Cancels a scheduled change of the retention period. */
    @DeleteMapping("/retention/scheduled")
    @RequirePermission(PermissionsConstants.MANAGE_PLATFORM_BILLING)
    public RetentionSettings cancelScheduledRetention() {
        return complianceService.cancelScheduledRetention();
    }

    @GetMapping("/subprocessor-notices")
    @RequirePermission(PermissionsConstants.VIEW_PLATFORM_TENANTS)
    public List<SubprocessorNoticeResponse> notices() {
        return complianceService.notices();
    }

    /** How many workspaces a notice would reach now, shown before sending. */
    @GetMapping("/subprocessor-notices/recipients")
    @RequirePermission(PermissionsConstants.MANAGE_PLATFORM_BILLING)
    public Map<String, Integer> recipients() {
        return Map.of("count", complianceService.noticeRecipientCount());
    }

    /** Emails the notice to every customer whose data we hold, and records it. */
    @PostMapping("/subprocessor-notices")
    @RequirePermission(PermissionsConstants.MANAGE_PLATFORM_BILLING)
    public SubprocessorNoticeResponse send(@Valid @RequestBody SubprocessorNoticeRequest request) {
        return complianceService.sendSubprocessorNotice(request);
    }
}
