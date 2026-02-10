package com.humano.service.hr;

import com.humano.domain.hr.Benefit;
import com.humano.repository.hr.BenefitRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreateBenefitRequest;
import com.humano.service.hr.dto.requests.UpdateBenefitRequest;
import com.humano.service.hr.dto.responses.BenefitResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing benefits.
 * Handles CRUD operations for benefit entities.
 */
@Service
public class BenefitService {

    private static final Logger log = LoggerFactory.getLogger(BenefitService.class);

    private final BenefitRepository benefitRepository;

    public BenefitService(BenefitRepository benefitRepository) {
        this.benefitRepository = benefitRepository;
    }

    /**
     * Create a new benefit.
     *
     * @param request the benefit creation request
     * @return the created benefit response
     */
    @Transactional
    public BenefitResponse createBenefit(CreateBenefitRequest request) {
        log.debug("Request to create Benefit: {}", request);

        Benefit benefit = new Benefit();
        benefit.setName(request.name());
        benefit.setAmount(request.amount());
        benefit.setDescription(request.description());

        Benefit savedBenefit = benefitRepository.save(benefit);
        log.info("Created benefit with ID: {}", savedBenefit.getId());

        return mapToResponse(savedBenefit);
    }

    /**
     * Update an existing benefit.
     *
     * @param id the ID of the benefit to update
     * @param request the benefit update request
     * @return the updated benefit response
     */
    @Transactional
    public BenefitResponse updateBenefit(UUID id, UpdateBenefitRequest request) {
        log.debug("Request to update Benefit: {}", id);

        return benefitRepository
            .findById(id)
            .map(benefit -> {
                if (request.name() != null) {
                    benefit.setName(request.name());
                }
                if (request.amount() != null) {
                    benefit.setAmount(request.amount());
                }
                if (request.description() != null) {
                    benefit.setDescription(request.description());
                }
                return mapToResponse(benefitRepository.save(benefit));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Benefit", id));
    }

    /**
     * Get a benefit by ID.
     *
     * @param id the ID of the benefit
     * @return the benefit response
     */
    @Transactional(readOnly = true)
    public BenefitResponse getBenefitById(UUID id) {
        log.debug("Request to get Benefit by ID: {}", id);

        return benefitRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Benefit", id));
    }

    /**
     * Get all benefits with pagination.
     *
     * @param pageable pagination information
     * @return page of benefit responses
     */
    @Transactional(readOnly = true)
    public Page<BenefitResponse> getAllBenefits(Pageable pageable) {
        log.debug("Request to get all Benefits");

        return benefitRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Delete a benefit by ID.
     *
     * @param id the ID of the benefit to delete
     */
    @Transactional
    public void deleteBenefit(UUID id) {
        log.debug("Request to delete Benefit: {}", id);

        if (!benefitRepository.existsById(id)) {
            throw EntityNotFoundException.create("Benefit", id);
        }
        benefitRepository.deleteById(id);
        log.info("Deleted benefit with ID: {}", id);
    }

    private BenefitResponse mapToResponse(Benefit benefit) {
        return new BenefitResponse(
            benefit.getId(),
            benefit.getName(),
            benefit.getAmount(),
            benefit.getDescription(),
            benefit.getEmployees() != null ? benefit.getEmployees().size() : 0,
            benefit.getCreatedBy(),
            benefit.getCreatedDate(),
            benefit.getLastModifiedBy(),
            benefit.getLastModifiedDate()
        );
    }
}
