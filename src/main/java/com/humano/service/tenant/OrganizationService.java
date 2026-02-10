package com.humano.service.tenant;

import com.humano.domain.tenant.Organization;
import com.humano.domain.tenant.Tenant;
import com.humano.repository.tenant.OrganizationRepository;
import com.humano.repository.tenant.TenantRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.tenant.dto.requests.CreateOrganizationRequest;
import com.humano.service.tenant.dto.requests.UpdateOrganizationRequest;
import com.humano.service.tenant.dto.responses.OrganizationResponse;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing organizations.
 * Handles CRUD operations for organization entities.
 */
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;

    public OrganizationService(OrganizationRepository organizationRepository, TenantRepository tenantRepository) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
    }

    /**
     * Create a new organization.
     *
     * @param request the organization creation request
     * @return the created organization response
     */
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        log.debug("Request to create Organization: {}", request);

        Tenant tenant = tenantRepository
            .findById(request.tenantId())
            .orElseThrow(() -> EntityNotFoundException.create("Tenant", request.tenantId()));

        Organization organization = new Organization();
        organization.setName(request.name());
        organization.setTenant(tenant);

        Organization savedOrganization = organizationRepository.save(organization);
        log.info("Created organization with ID: {}", savedOrganization.getId());

        return mapToResponse(savedOrganization);
    }

    /**
     * Update an existing organization.
     *
     * @param id the ID of the organization to update
     * @param request the organization update request
     * @return the updated organization response
     */
    @Transactional
    public OrganizationResponse updateOrganization(UUID id, UpdateOrganizationRequest request) {
        log.debug("Request to update Organization: {}", id);

        return organizationRepository
            .findById(id)
            .map(organization -> {
                if (request.name() != null) {
                    organization.setName(request.name());
                }
                return mapToResponse(organizationRepository.save(organization));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Organization", id));
    }

    /**
     * Get an organization by ID.
     *
     * @param id the ID of the organization
     * @return the organization response
     */
    @Transactional(readOnly = true)
    public OrganizationResponse getOrganizationById(UUID id) {
        log.debug("Request to get Organization by ID: {}", id);

        return organizationRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("Organization", id));
    }

    /**
     * Get all organizations with pagination.
     *
     * @param pageable pagination information
     * @return page of organization responses
     */
    @Transactional(readOnly = true)
    public Page<OrganizationResponse> getAllOrganizations(Pageable pageable) {
        log.debug("Request to get all Organizations");

        return organizationRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get organizations by tenant.
     *
     * @param tenantId the tenant ID
     * @return list of organization responses
     */
    @Transactional(readOnly = true)
    public List<OrganizationResponse> getOrganizationsByTenant(UUID tenantId) {
        log.debug("Request to get Organizations by Tenant: {}", tenantId);

        return organizationRepository.findByTenantId(tenantId).stream().map(this::mapToResponse).toList();
    }

    /**
     * Delete an organization by ID.
     *
     * @param id the ID of the organization to delete
     */
    @Transactional
    public void deleteOrganization(UUID id) {
        log.debug("Request to delete Organization: {}", id);

        if (!organizationRepository.existsById(id)) {
            throw EntityNotFoundException.create("Organization", id);
        }
        organizationRepository.deleteById(id);
        log.info("Deleted organization with ID: {}", id);
    }

    private OrganizationResponse mapToResponse(Organization organization) {
        return new OrganizationResponse(
            organization.getId(),
            organization.getName(),
            organization.getTenant().getId(),
            organization.getTenant().getName(),
            organization.getCreatedBy(),
            organization.getCreatedDate(),
            organization.getLastModifiedBy(),
            organization.getLastModifiedDate()
        );
    }
}
