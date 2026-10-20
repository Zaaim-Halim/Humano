package com.humano.service.demo;

import com.humano.domain.enumeration.tenant.DemoRequestStatus;
import com.humano.domain.tenant.DemoRequest;
import com.humano.domain.tenant.Tenant;
import com.humano.dto.demo.DemoRequestResponse;
import com.humano.dto.demo.SubmitDemoRequest;
import com.humano.dto.demo.UpdateDemoRequestRequest;
import com.humano.repository.tenant.DemoRequestRepository;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Demo requests: received from the public site, worked by the platform team. */
@Service
@Transactional
public class DemoRequestService {

    private static final Logger log = LoggerFactory.getLogger(DemoRequestService.class);
    private static final String ENTITY_NAME = "demoRequest";

    private final DemoRequestRepository repository;

    public DemoRequestService(DemoRequestRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a request from the public form. A filled-in honeypot or a repeat from the same address
     * within ten minutes is accepted without storing anything, so neither bots nor double clicks
     * learn or create anything.
     */
    public void submit(SubmitDemoRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Ignored a demo request with the honeypot filled in");
            return;
        }
        String email = request.workEmail().trim().toLowerCase();
        if (repository.existsByWorkEmailIgnoreCaseAndCreatedDateAfter(email, Instant.now().minus(10, ChronoUnit.MINUTES))) {
            return;
        }
        DemoRequest entity = new DemoRequest();
        entity.setFullName(request.fullName().trim());
        entity.setWorkEmail(email);
        entity.setCompanyName(request.companyName().trim());
        entity.setCompanySize(blankToNull(request.companySize()));
        entity.setCountry(request.country());
        entity.setPhone(blankToNull(request.phone()));
        entity.setMessage(blankToNull(request.message()));
        entity.setStatus(DemoRequestStatus.NEW);
        repository.save(entity);
        log.info("Demo request received from {}", entity.getCompanyName());
    }

    @Transactional(readOnly = true)
    public Page<DemoRequestResponse> list(DemoRequestStatus status, Pageable pageable) {
        Page<DemoRequest> page = status == null
            ? repository.findAllByOrderByCreatedDateDesc(pageable)
            : repository.findByStatusOrderByCreatedDateDesc(status, pageable);
        return page.map(DemoRequestService::toResponse);
    }

    @Transactional(readOnly = true)
    public long countNew() {
        return repository.countByStatus(DemoRequestStatus.NEW);
    }

    public DemoRequestResponse update(UUID id, UpdateDemoRequestRequest request) {
        DemoRequest entity = find(id);
        if (request.status() != null) {
            if (request.status() == DemoRequestStatus.DEMO_CREATED) {
                throw new BadRequestAlertException("Create a demo to mark a request as done", ENTITY_NAME, "statusreserved");
            }
            entity.setStatus(request.status());
        }
        if (request.notes() != null) {
            entity.setNotes(blankToNull(request.notes()));
        }
        return toResponse(repository.save(entity));
    }

    public void delete(UUID id) {
        repository.delete(find(id));
    }

    /** Links the demo workspace set up for this request (called by {@link DemoWorkspaceService}). */
    void markDemoCreated(UUID id, Tenant tenant) {
        DemoRequest entity = find(id);
        entity.setStatus(DemoRequestStatus.DEMO_CREATED);
        entity.setTenant(tenant);
        repository.save(entity);
    }

    private DemoRequest find(UUID id) {
        return repository.findById(id).orElseThrow(() -> EntityNotFoundException.create("DemoRequest", id));
    }

    private static DemoRequestResponse toResponse(DemoRequest r) {
        Tenant t = r.getTenant();
        return new DemoRequestResponse(
            r.getId(),
            r.getFullName(),
            r.getWorkEmail(),
            r.getCompanyName(),
            r.getCompanySize(),
            r.getCountry(),
            r.getPhone(),
            r.getMessage(),
            r.getStatus(),
            r.getNotes(),
            t == null ? null : t.getId(),
            t == null ? null : t.getSubdomain(),
            r.getCreatedDate(),
            r.getLastModifiedDate()
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
