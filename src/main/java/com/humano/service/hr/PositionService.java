package com.humano.service.hr;

import com.humano.domain.hr.OrganizationalUnit;
import com.humano.domain.hr.Position;
import com.humano.dto.hr.requests.CreatePositionRequest;
import com.humano.dto.hr.requests.UpdatePositionRequest;
import com.humano.dto.hr.responses.PositionResponse;
import com.humano.repository.hr.OrganizationalUnitRepository;
import com.humano.repository.hr.PositionRepository;
import com.humano.repository.hr.projection.EmployeeCountRow;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.util.HashMap;
import java.util.Map;
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
    private final EmployeeRepository employeeRepository;

    public PositionService(
        PositionRepository positionRepository,
        OrganizationalUnitRepository organizationalUnitRepository,
        EmployeeRepository employeeRepository
    ) {
        this.positionRepository = positionRepository;
        this.organizationalUnitRepository = organizationalUnitRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional("tenantTransactionManager")
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

    @Transactional("tenantTransactionManager")
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

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PositionResponse getPositionById(UUID id) {
        log.debug("Request to get Position by ID: {}", id);

        return positionRepository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create("Position", id));
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PositionResponse> getAllPositions(Pageable pageable) {
        log.debug("Request to get all Positions");

        Map<UUID, Long> counts = headcounts();
        return positionRepository.findAll(pageable).map(p -> mapToResponse(p, counts.getOrDefault(p.getId(), 0L)));
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PositionResponse> getPositionsByUnit(UUID unitId, Pageable pageable) {
        log.debug("Request to get Positions by Unit: {}", unitId);

        Map<UUID, Long> counts = headcounts();
        return positionRepository.findByUnitId(unitId, pageable).map(p -> mapToResponse(p, counts.getOrDefault(p.getId(), 0L)));
    }

    @Transactional("tenantTransactionManager")
    public void deletePosition(UUID id) {
        log.debug("Request to delete Position: {}", id);

        if (!positionRepository.existsById(id)) {
            throw EntityNotFoundException.create("Position", id);
        }
        positionRepository.deleteById(id);
        log.info("Deleted position with ID: {}", id);
    }

    /** One GROUP BY for every position's headcount, instead of loading each employee collection. */
    private Map<UUID, Long> headcounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (EmployeeCountRow row : employeeRepository.countByPosition()) {
            counts.put((UUID) row.key(), row.count());
        }
        return counts;
    }

    private PositionResponse mapToResponse(Position position) {
        return mapToResponse(position, position.getId() == null ? 0 : employeeRepository.countByPositionId(position.getId()));
    }

    private PositionResponse mapToResponse(Position position, long employeeCount) {
        return new PositionResponse(
            position.getId(),
            position.getName(),
            position.getDescription(),
            position.getLevel(),
            position.getUnit() != null ? position.getUnit().getId() : null,
            position.getUnit() != null ? position.getUnit().getName() : null,
            position.getParentPosition() != null ? position.getParentPosition().getId() : null,
            position.getParentPosition() != null ? position.getParentPosition().getName() : null,
            (int) employeeCount,
            position.getCreatedBy(),
            position.getCreatedDate(),
            position.getLastModifiedBy(),
            position.getLastModifiedDate()
        );
    }
}
