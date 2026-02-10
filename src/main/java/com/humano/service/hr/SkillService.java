package com.humano.service.hr;

import com.humano.domain.hr.Skill;
import com.humano.repository.hr.SkillRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreateSkillRequest;
import com.humano.service.hr.dto.requests.UpdateSkillRequest;
import com.humano.service.hr.dto.responses.SkillResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing skills.
 * Handles CRUD operations for skill entities.
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * Create a new skill.
     *
     * @param request the skill creation request
     * @return the created skill response
     */
    @Transactional
    public SkillResponse createSkill(CreateSkillRequest request) {
        log.debug("Request to create Skill: {}", request);

        Skill skill = new Skill();
        skill.setName(request.name());
        skill.setDescription(request.description());
        skill.setCategory(request.category());
        skill.setRequiresCertification(request.requiresCertification() != null ? request.requiresCertification() : false);

        Skill savedSkill = skillRepository.save(skill);
        log.info("Created skill with ID: {}", savedSkill.getId());

        return mapToResponse(savedSkill);
    }

    /**
     * Update an existing skill.
     *
     * @param id the ID of the skill to update
     * @param request the skill update request
     * @return the updated skill response
     */
    @Transactional
    public SkillResponse updateSkill(UUID id, UpdateSkillRequest request) {
        log.debug("Request to update Skill: {}", id);

        return skillRepository
            .findById(id)
            .map(skill -> {
                if (request.name() != null) {
                    skill.setName(request.name());
                }
                if (request.description() != null) {
                    skill.setDescription(request.description());
                }
                if (request.category() != null) {
                    skill.setCategory(request.category());
                }
                if (request.requiresCertification() != null) {
                    skill.setRequiresCertification(request.requiresCertification());
                }
                return mapToResponse(skillRepository.save(skill));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Skill", id));
    }

    /**
     * Get a skill by ID.
     *
     * @param id the ID of the skill
     * @return the skill response
     */
    @Transactional(readOnly = true)
    public SkillResponse getSkillById(UUID id) {
        log.debug("Request to get Skill by ID: {}", id);

        return skillRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Skill", id));
    }

    /**
     * Get all skills with pagination.
     *
     * @param pageable pagination information
     * @return page of skill responses
     */
    @Transactional(readOnly = true)
    public Page<SkillResponse> getAllSkills(Pageable pageable) {
        log.debug("Request to get all Skills");

        return skillRepository.findAll(pageable).map(this::mapToResponse);
    }

    /**
     * Get skills by category.
     *
     * @param category the skill category
     * @param pageable pagination information
     * @return page of skill responses
     */
    @Transactional(readOnly = true)
    public Page<SkillResponse> getSkillsByCategory(String category, Pageable pageable) {
        log.debug("Request to get Skills by Category: {}", category);

        return skillRepository.findByCategory(category, pageable).map(this::mapToResponse);
    }

    /**
     * Delete a skill by ID.
     *
     * @param id the ID of the skill to delete
     */
    @Transactional
    public void deleteSkill(UUID id) {
        log.debug("Request to delete Skill: {}", id);

        if (!skillRepository.existsById(id)) {
            throw EntityNotFoundException.create("Skill", id);
        }
        skillRepository.deleteById(id);
        log.info("Deleted skill with ID: {}", id);
    }

    private SkillResponse mapToResponse(Skill skill) {
        return new SkillResponse(
            skill.getId(),
            skill.getName(),
            skill.getDescription(),
            skill.getCategory(),
            skill.getRequiresCertification(),
            skill.getEmployeeSkills() != null ? skill.getEmployeeSkills().size() : 0,
            skill.getCreatedBy(),
            skill.getCreatedDate(),
            skill.getLastModifiedBy(),
            skill.getLastModifiedDate()
        );
    }
}
