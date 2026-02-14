package com.humano.service.tenant;

import com.humano.domain.billing.SubscriptionPlan;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.tenant.requests.CreateTenantRequest;
import com.humano.dto.tenant.requests.UpdateTenantRequest;
import com.humano.dto.tenant.responses.TenantResponse;
import com.humano.repository.billing.SubscriptionPlanRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing tenants.
 * Handles CRUD operations for tenant entities.
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public TenantService(TenantRepository tenantRepository, SubscriptionPlanRepository subscriptionPlanRepository) {
        this.tenantRepository = tenantRepository;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    /**
     * Create a new tenant.
     *
     * @param request the tenant creation request
     * @return the created tenant response
     */
    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        log.debug("Request to create Tenant: {}", request);

        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository
            .findById(request.subscriptionPlanId())
            .orElseThrow(() -> EntityNotFoundException.create("SubscriptionPlan", request.subscriptionPlanId()));

        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setDomain(request.domain());
        tenant.setSubdomain(request.subdomain());
        tenant.setLogo(request.logo());
        tenant.setTimezone(request.timezone());
        tenant.setBookingPolicies(request.bookingPolicies());
        tenant.setHrPolicies(request.hrPolicies());
        tenant.setSubscriptionPlan(subscriptionPlan);

        Tenant savedTenant = tenantRepository.save(tenant);
        log.info("Created tenant with ID: {}", savedTenant.getId());

        return mapToResponse(savedTenant);
    }

    /**
     * Update an existing tenant.
     *
     * @param id the ID of the tenant to update
     * @param request the tenant update request
     * @return the updated tenant response
     */
    @Transactional
    public TenantResponse updateTenant(UUID id, UpdateTenantRequest request) {
        log.debug("Request to update Tenant: {}", id);

        return tenantRepository
            .findById(id)
            .map(tenant -> {
                if (request.name() != null) {
                    tenant.setName(request.name());
                }
                if (request.domain() != null) {
                    tenant.setDomain(request.domain());
                }
                if (request.subdomain() != null) {
                    tenant.setSubdomain(request.subdomain());
                }
                if (request.logo() != null) {
                    tenant.setLogo(request.logo());
                }
                if (request.timezone() != null) {
                    tenant.setTimezone(request.timezone());
                }
                if (request.bookingPolicies() != null) {
                    tenant.setBookingPolicies(request.bookingPolicies());
                }
                if (request.hrPolicies() != null) {
                    tenant.setHrPolicies(request.hrPolicies());
                }
                return mapToResponse(tenantRepository.save(tenant));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Tenant", id));
    }

    /**
     * Get a tenant by ID.
     *
     * @param id the ID of the tenant
     * @return the tenant response
     */
    @Transactional(readOnly = true)
    public TenantResponse getTenantById(UUID id) {
        log.debug("Request to get Tenant by ID: {}", id);

        return tenantRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Tenant", id));
    }

    /**
     * Get a tenant by subdomain.
     *
     * @param subdomain the subdomain of the tenant
     * @return the tenant response if found
     */
    @Transactional(readOnly = true)
    public Optional<TenantResponse> getTenantBySubdomain(String subdomain) {
        log.debug("Request to get Tenant by subdomain: {}", subdomain);

        return tenantRepository.findBySubdomain(subdomain).map(this::mapToResponse);
    }

    /**
     * Get a tenant by domain.
     *
     * @param domain the domain of the tenant
     * @return the tenant response if found
     */
    @Transactional(readOnly = true)
    public Optional<TenantResponse> getTenantByDomain(String domain) {
        log.debug("Request to get Tenant by domain: {}", domain);

        return tenantRepository.findByDomain(domain).map(this::mapToResponse);
    }

    /**
     * Get all tenants with pagination.
     *
     * @param pageable pagination information
     * @return page of tenant responses
     */
    @Transactional(readOnly = true)
    public Page<TenantResponse> getAllTenants(Pageable pageable) {
        log.debug("Request to get all Tenants");

        return tenantRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Delete a tenant by ID.
     *
     * @param id the ID of the tenant to delete
     */
    @Transactional
    public void deleteTenant(UUID id) {
        log.debug("Request to delete Tenant: {}", id);

        if (!tenantRepository.existsById(id)) {
            throw EntityNotFoundException.create("Tenant", id);
        }
        tenantRepository.deleteById(id);
        log.info("Deleted tenant with ID: {}", id);
    }

    /**
     * Check if a domain is available.
     *
     * @param domain the domain to check
     * @return true if the domain is available, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isDomainAvailable(String domain) {
        log.debug("Request to check domain availability: {}", domain);
        return !tenantRepository.existsByDomain(domain);
    }

    /**
     * Check if a subdomain is available.
     *
     * @param subdomain the subdomain to check
     * @return true if the subdomain is available, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isSubdomainAvailable(String subdomain) {
        log.debug("Request to check subdomain availability: {}", subdomain);
        return !tenantRepository.existsBySubdomain(subdomain);
    }

    /**
     * Map a Tenant entity to a TenantResponse DTO.
     *
     * @param tenant the tenant entity
     * @return the tenant response DTO
     */
    private TenantResponse mapToResponse(Tenant tenant) {
        return new TenantResponse(
            tenant.getId(),
            tenant.getName(),
            tenant.getDomain(),
            tenant.getSubdomain(),
            tenant.getLogo(),
            tenant.getTimezone(),
            tenant.getStatus(),
            tenant.getBookingPolicies(),
            tenant.getHrPolicies(),
            tenant.getSubscriptionPlan().getId(),
            tenant.getSubscriptionPlan().getDisplayName(),
            tenant.getOrganizations().size(),
            tenant.getCreatedBy(),
            tenant.getCreatedDate(),
            tenant.getLastModifiedBy(),
            tenant.getLastModifiedDate()
        );
    }
}
