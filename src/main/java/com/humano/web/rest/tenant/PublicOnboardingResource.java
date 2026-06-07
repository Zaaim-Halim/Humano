package com.humano.web.rest.tenant;

import com.humano.dto.tenant.requests.TenantOnboardingRequest;
import com.humano.dto.tenant.responses.TenantOnboardingResponse;
import com.humano.security.annotation.PublicEndpoint;
import com.humano.service.tenant.TenantOnboardingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated tenant signup. Delegates to the existing
 * {@link TenantOnboardingService#onboardTenant(TenantOnboardingRequest)} which provisions
 * the tenant, creates the admin user, and issues an initial invoice + subscription.
 *
 * <p>Security: {@code permitAll} via {@code SecurityConfiguration}; CSRF disabled for this
 * single path because non-SPA clients (curl / mobile / partner integration) won't have a
 * pre-fetched CSRF cookie. Tenant resolution is also skipped — {@code TenantResolutionFilter}
 * excludes {@code /api/tenant-registration} so the call hits the master DB.
 *
 * <p>Rate limiting is wired separately in the security configuration.
 */
@RestController
@RequestMapping("/api/tenant-registration")
public class PublicOnboardingResource {

    private static final Logger LOG = LoggerFactory.getLogger(PublicOnboardingResource.class);

    private final TenantOnboardingService onboardingService;

    public PublicOnboardingResource(TenantOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PublicEndpoint
    public TenantOnboardingResponse onboard(@Valid @RequestBody TenantOnboardingRequest request) {
        LOG.info("Public onboarding request received for company '{}'", request.companyName());
        return onboardingService.onboardTenant(request);
    }
}
