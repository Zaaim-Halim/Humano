package com.humano.service.hr;

import com.humano.domain.shared.AbstractReferenceData;
import com.humano.dto.hr.requests.CreateReferenceDataRequest;
import com.humano.dto.hr.requests.UpdateReferenceDataRequest;
import com.humano.dto.hr.responses.ReferenceDataResponse;
import com.humano.service.errors.EntityNotFoundException;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared CRUD logic for tenant-configurable reference-data entities backed by
 * {@link AbstractReferenceData}. Concrete services supply the repository, a factory for new
 * instances and the entity name used in error messages.
 */
public abstract class AbstractReferenceDataService<T extends AbstractReferenceData> {

    private final JpaRepository<T, UUID> repository;
    private final Supplier<T> factory;
    private final String entityName;

    protected AbstractReferenceDataService(JpaRepository<T, UUID> repository, Supplier<T> factory, String entityName) {
        this.repository = repository;
        this.factory = factory;
        this.entityName = entityName;
    }

    @Transactional
    public ReferenceDataResponse create(CreateReferenceDataRequest request) {
        T entity = factory.get();
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setActive(request.active() != null ? request.active() : true);
        entity.setNotes(request.notes());
        return mapToResponse(repository.save(entity));
    }

    @Transactional
    public ReferenceDataResponse update(UUID id, UpdateReferenceDataRequest request) {
        return repository
            .findById(id)
            .map(entity -> {
                if (request.code() != null) {
                    entity.setCode(request.code());
                }
                if (request.name() != null) {
                    entity.setName(request.name());
                }
                if (request.active() != null) {
                    entity.setActive(request.active());
                }
                if (request.notes() != null) {
                    entity.setNotes(request.notes());
                }
                return mapToResponse(repository.save(entity));
            })
            .orElseThrow(() -> EntityNotFoundException.create(entityName, id));
    }

    @Transactional(readOnly = true)
    public ReferenceDataResponse getById(UUID id) {
        return repository.findById(id).map(this::mapToResponse).orElseThrow(() -> EntityNotFoundException.create(entityName, id));
    }

    @Transactional(readOnly = true)
    public Page<ReferenceDataResponse> getAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw EntityNotFoundException.create(entityName, id);
        }
        repository.deleteById(id);
    }

    protected ReferenceDataResponse mapToResponse(T e) {
        return new ReferenceDataResponse(
            e.getId(),
            e.getCode(),
            e.getName(),
            e.getActive(),
            e.getNotes(),
            e.getCreatedBy(),
            e.getCreatedDate(),
            e.getLastModifiedBy(),
            e.getLastModifiedDate()
        );
    }
}
