package com.humano.service.hr;

import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreatePositionRequest;
import com.humano.service.hr.dto.requests.UpdatePositionRequest;
import com.humano.service.hr.dto.responses.PositionResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);

    private final PositionRepository positionRepository;
    private final OrganizationalUnitRepository organizationalUnitRepository;

    public PositionService(PositionRepository positionRepository, OrganizationalUnitRepository organizationalUnitRepository) {
        this.positionRepository = positionRepository;
        this.organizationalUnitRepository = organizationalUnitRepository;
    }

    @Transactional
    public PositionResponse createPosition(CreatePositionRequest request) {
        log.debug("Request to create Position: {}", request);

        Position position = new Position();
        position.setName(request.name());
        position.setDescription(request.description());
        position.setLevel(request.level());

        if (request.unitId() != null) {
            OrganizationalUnit unit = organizationalUnitRepository
                .findById(request.unitId())
                .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", request.unitId()));
            position.setUnit(unit);
        }

        if (request.parentPositionId() != null) {
            Position parentPosition = positionRepository
                .findById(request.parentPositionId())
                .orElseThrow(() -> EntityNotFoundException.create("Position", request.parentPositionId()));
            position.setParentPosition(parentPosition);
        }

        Position savedPosition = positionRepository.save(position);
        log.info("Created position with ID: {}", savedPosition.getId());

        return mapToResponse(savedPosition);
    }

    @Transactional
    public PositionResponse updatePosition(UUID id, UpdatePositionRequest request) {
        log.debug("Request to update Position: {}", id);

        return positionRepository
            .findById(id)
            .map(position -> {
                if (request.name() != null) {
                    position.setName(request.name());
                }
                if (request.description() != null) {
                    position.setDescription(request.description());
                }
                if (request.level() != null) {
                    position.setLevel(request.level());
                }
                if (request.unitId() != null) {
                    OrganizationalUnit unit = organizationalUnitRepository
                        .findById(request.unitId())
                        .orElseThrow(() -> EntityNotFoundException.create("OrganizationalUnit", request.unitId()));
                    position.setUnit(unit);
                }
                if (request.parentPositionId() != null) {
                    Position parentPosition = positionRepository
                        .findById(request.parentPositionId())
                        .orElseThrow(() -> EntityNotFoundException.create("Position", request.parentPositionId()));
                    position.setParentPosition(parentPosition);
                }
                return mapToResponse(positionRepository.save(position));
            })
            .orElseThrow(() -> EntityNotFoundException.create("Position", id));
    }

    @Transactional(readOnly = true)
    public PositionResponse getPositionById(UUID id) {
        log.debug("Request to get Position by ID: {}", id);

        return positionRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Position", id));
    }

    @Transactional(readOnly = true)
    public Page<PositionResponse> getAllPositions(Pageable pageable) {
        log.debug("Request to get all Positions");

        return positionRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<PositionResponse> getPositionsByUnit(UUID unitId, Pageable pageable) {
        log.debug("Request to get Positions by Unit: {}", unitId);

        return positionRepository.findByUnitId(unitId, pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deletePosition(UUID id) {
        log.debug("Request to delete Position: {}", id);

        if (!positionRepository.existsById(id)) {
            throw EntityNotFoundException.create("Position", id);
        }
        positionRepository.deleteById(id);
        log.info("Deleted position with ID: {}", id);
    }

    private PositionResponse mapToResponse(Position position) {
        return new PositionResponse(
            position.getId(),
            position.getName(),
            position.getDescription(),
            position.getLevel(),
            position.getUnit() != null ? position.getUnit().getId() : null,
            position.getUnit() != null ? position.getUnit().getName() : null,
            position.getParentPosition() != null ? position.getParentPosition().getId() : null,
            position.getParentPosition() != null ? position.getParentPosition().getName() : null,
            position.getEmployees() != null ? position.getEmployees().size() : 0,
            position.getCreatedBy(),
            position.getCreatedDate(),
            position.getLastModifiedBy(),
            position.getLastModifiedDate()
        );
    }
}
