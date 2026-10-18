package com.humano.web.rest.hr.workflow;

import com.humano.dto.hr.workflow.requests.UpdateOnboardingChecklistRequest;
import com.humano.dto.hr.workflow.responses.OnboardingChecklistResponse;
import com.humano.security.PermissionsConstants;
import com.humano.security.annotation.RequirePermission;
import com.humano.service.hr.workflow.OnboardingChecklistService;
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
 * REST controller for the company onboarding policy
 * ({@code /api/hr/workflows/onboarding-checklist}): the default onboarding
 * length and the checklist new onboardings are built from. Read by anyone who
 * can see onboarding (the start panel needs the default length); replaced with
 * {@code MANAGE_ONBOARDING}.
 */
@RestController
@RequestMapping("/api/hr/workflows/onboarding-checklist")
public class OnboardingChecklistResource {

    private static final Logger LOG = LoggerFactory.getLogger(OnboardingChecklistResource.class);

    private final OnboardingChecklistService service;

    public OnboardingChecklistResource(OnboardingChecklistService service) {
        this.service = service;
    }

    /** {@code GET /} : the default length and checklist (built-in until customised). */
    @GetMapping
    @PreAuthorize(
        "@securityExpressions.hasAnyPermission('" +
        PermissionsConstants.VIEW_ONBOARDING_PROGRESS +
        "', '" +
        PermissionsConstants.MANAGE_ONBOARDING +
        "')"
    )
    public ResponseEntity<OnboardingChecklistResponse> get() {
        LOG.debug("REST request to get the onboarding checklist");
        return ResponseEntity.ok(service.get());
    }

    /** {@code PUT /} : replace the default length and the whole checklist. */
    @PutMapping
    @RequirePermission(PermissionsConstants.MANAGE_ONBOARDING)
    public ResponseEntity<OnboardingChecklistResponse> update(@Valid @RequestBody UpdateOnboardingChecklistRequest request) {
        LOG.debug("REST request to update the onboarding checklist ({} tasks)", request.tasks().size());
        return ResponseEntity.ok(service.update(request));
    }
}
