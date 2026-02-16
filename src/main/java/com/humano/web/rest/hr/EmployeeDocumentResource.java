package com.humano.web.rest.hr;

import com.humano.dto.hr.requests.CreateEmployeeDocumentRequest;
import com.humano.dto.hr.requests.UpdateEmployeeDocumentRequest;
import com.humano.dto.hr.responses.EmployeeDocumentResponse;
import com.humano.security.AuthoritiesConstants;
import com.humano.service.hr.EmployeeDocumentService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing employee documents.
 */
@RestController
@RequestMapping("/api/hr/employee-documents")
public class EmployeeDocumentResource {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeDocumentResource.class);
    private static final String ENTITY_NAME = "employeeDocument";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final EmployeeDocumentService employeeDocumentService;

    public EmployeeDocumentResource(EmployeeDocumentService employeeDocumentService) {
        this.employeeDocumentService = employeeDocumentService;
    }

    /**
     * {@code POST  /employee-documents/employee/{employeeId}} : Upload a new document for an employee.
     *
     * @param employeeId the ID of the employee
     * @param request the document metadata
     * @param file the document file
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new document
     * @throws URISyntaxException if the Location URI syntax is incorrect
     * @throws IOException if file upload fails
     */
    @PostMapping("/employee/{employeeId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<EmployeeDocumentResponse> uploadDocument(
        @PathVariable UUID employeeId,
        @Valid @RequestPart("metadata") CreateEmployeeDocumentRequest request,
        @RequestPart("file") MultipartFile file
    ) throws URISyntaxException, IOException {
        LOG.debug("REST request to upload Document for employee {}: {}", employeeId, request);

        EmployeeDocumentResponse result = employeeDocumentService.uploadDocument(employeeId, request, file);

        return ResponseEntity.created(new URI("/api/hr/employee-documents/" + result.id()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.id().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /employee-documents/{id}} : Updates an existing document metadata.
     *
     * @param id the ID of the document to update
     * @param request the document update request
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated document
     */
    @PutMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<EmployeeDocumentResponse> updateDocument(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateEmployeeDocumentRequest request
    ) {
        LOG.debug("REST request to update Document: {}", id);

        EmployeeDocumentResponse result = employeeDocumentService.updateDocument(id, request);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code PUT  /employee-documents/{id}/file} : Replace the file of an existing document.
     *
     * @param id the ID of the document
     * @param file the new file
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated document
     * @throws IOException if file upload fails
     */
    @PutMapping("/{id}/file")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<EmployeeDocumentResponse> replaceDocumentFile(@PathVariable UUID id, @RequestPart("file") MultipartFile file)
        throws IOException {
        LOG.debug("REST request to replace file for Document: {}", id);

        EmployeeDocumentResponse result = employeeDocumentService.replaceDocumentFile(id, file);

        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .body(result);
    }

    /**
     * {@code GET  /employee-documents} : Get all documents with pagination.
     *
     * @param pageable the pagination information
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of documents in body
     */
    @GetMapping
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "')"
    )
    public ResponseEntity<Page<EmployeeDocumentResponse>> getAllDocuments(Pageable pageable) {
        LOG.debug("REST request to get all Documents");

        Page<EmployeeDocumentResponse> page = employeeDocumentService.getAllDocuments(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);

        return ResponseEntity.ok().headers(headers).body(page);
    }

    /**
     * {@code GET  /employee-documents/{id}} : Get document by ID.
     *
     * @param id the ID of the document to retrieve
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the document
     */
    @GetMapping("/{id}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<EmployeeDocumentResponse> getDocument(@PathVariable UUID id) {
        LOG.debug("REST request to get Document: {}", id);

        EmployeeDocumentResponse result = employeeDocumentService.getDocument(id);

        return ResponseEntity.ok(result);
    }

    /**
     * {@code GET  /employee-documents/{id}/download} : Download document content.
     *
     * @param id the ID of the document to download
     * @return the {@link ResponseEntity} with the document content
     * @throws IOException if file download fails
     */
    @GetMapping("/{id}/download")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id) throws IOException {
        LOG.debug("REST request to download Document: {}", id);

        EmployeeDocumentResponse document = employeeDocumentService.getDocument(id);
        InputStream content = employeeDocumentService.getDocumentContent(id);

        // Extract filename from file path
        String fileName = document.filePath().substring(document.filePath().lastIndexOf('/') + 1);

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body(new InputStreamResource(content));
    }

    /**
     * {@code GET  /employee-documents/employee/{employeeId}} : Get documents by employee.
     *
     * @param employeeId the ID of the employee
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of documents in body
     */
    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "', '" +
        AuthoritiesConstants.HR_MANAGER +
        "', '" +
        AuthoritiesConstants.HR_SPECIALIST +
        "', '" +
        AuthoritiesConstants.EMPLOYEE +
        "')"
    )
    public ResponseEntity<List<EmployeeDocumentResponse>> getDocumentsByEmployee(@PathVariable UUID employeeId) {
        LOG.debug("REST request to get Documents by employee: {}", employeeId);

        List<EmployeeDocumentResponse> documents = employeeDocumentService.getDocumentsByEmployee(employeeId);

        return ResponseEntity.ok(documents);
    }

    /**
     * {@code DELETE  /employee-documents/{id}} : Delete document by ID.
     *
     * @param id the ID of the document to delete
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.ADMIN + "', '" + AuthoritiesConstants.HR_MANAGER + "')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        LOG.debug("REST request to delete Document: {}", id);

        employeeDocumentService.deleteDocument(id);

        return ResponseEntity.noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString()))
            .build();
    }
}
