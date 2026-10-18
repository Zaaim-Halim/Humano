package com.humano.service.hr;

import com.humano.domain.hr.EmployeeDocument;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeDocumentRequest;
import com.humano.dto.hr.requests.UpdateEmployeeDocumentRequest;
import com.humano.dto.hr.responses.EmployeeDocumentResponse;
import com.humano.repository.hr.EmployeeDocumentRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.service.billing.entitlement.StorageQuotaService;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.storage.FileStorageService;
import com.humano.service.storage.StorageFactory;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for managing employee documents.
 * Handles CRUD operations and file storage for employee-related documents.
 *
 * @author halimzaaim
 */
@Service
public class EmployeeDocumentService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeDocumentService.class);

    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final EmployeeRepository employeeRepository;
    private final StorageFactory storageFactory;
    private final StorageQuotaService storageQuotaService;

    public EmployeeDocumentService(
        EmployeeDocumentRepository employeeDocumentRepository,
        EmployeeRepository employeeRepository,
        StorageFactory storageFactory,
        StorageQuotaService storageQuotaService
    ) {
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.employeeRepository = employeeRepository;
        this.storageFactory = storageFactory;
        this.storageQuotaService = storageQuotaService;
    }

    private FileStorageService fileStorageService() {
        return storageFactory.getStorageService();
    }

    /**
     * Upload a new employee document.
     *
     * @param employeeId the ID of the employee
     * @param request the document metadata
     * @param file the document file to upload
     * @return the created employee document response
     * @throws IOException if file handling fails
     */
    @Transactional("tenantTransactionManager")
    public EmployeeDocumentResponse uploadDocument(UUID employeeId, CreateEmployeeDocumentRequest request, MultipartFile file)
        throws IOException {
        log.debug("Request to upload document for Employee ID: {}", employeeId);

        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with ID: " + employeeId));

        String type = request.type() == null || request.type().isBlank() ? "Other" : request.type().strip();
        validate(file);
        storageQuotaService.checkFits(file.getSize());

        EmployeeDocument document = new EmployeeDocument();
        document.setEmployee(employee);
        document.setType(type);
        document.setFilePath(storeFile(employee.getId(), type, file));
        applyFileMetadata(document, file);

        EmployeeDocument savedDocument = employeeDocumentRepository.save(document);
        log.info("Document uploaded successfully with ID: {}", savedDocument.getId());

        return mapToEmployeeDocumentResponse(savedDocument);
    }

    /**
     * Update an existing employee document's metadata.
     *
     * @param id the ID of the document to update
     * @param request the updated document metadata
     * @return the updated employee document response
     */
    @Transactional("tenantTransactionManager")
    public EmployeeDocumentResponse updateDocument(UUID id, UpdateEmployeeDocumentRequest request) {
        log.debug("Request to update document with ID: {}", id);

        return employeeDocumentRepository
            .findById(id)
            .map(document -> {
                if (request.type() != null) {
                    document.setType(request.type());
                }

                EmployeeDocument updatedDocument = employeeDocumentRepository.save(document);
                return mapToEmployeeDocumentResponse(updatedDocument);
            })
            .orElseThrow(() -> new EntityNotFoundException("Document not found with ID: " + id));
    }

    /**
     * Replace an existing employee document's file.
     *
     * @param id the ID of the document to replace
     * @param file the new document file
     * @return the updated employee document response
     * @throws IOException if file handling fails
     */
    @Transactional("tenantTransactionManager")
    public EmployeeDocumentResponse replaceDocumentFile(UUID id, MultipartFile file) throws IOException {
        log.debug("Request to replace file for document with ID: {}", id);

        EmployeeDocument document = employeeDocumentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Document not found with ID: " + id));

        validate(file);
        storageQuotaService.checkFits(file.getSize() - (document.getSizeBytes() != null ? document.getSizeBytes() : 0));
        String fileReference = storeFile(document.getEmployee().getId(), document.getType(), file);

        // Delete the old file if possible
        if (fileStorageService().exists(document.getFilePath())) {
            fileStorageService().delete(document.getFilePath());
        }

        // Update document entity
        document.setFilePath(fileReference);
        applyFileMetadata(document, file);

        EmployeeDocument updatedDocument = employeeDocumentRepository.save(document);
        log.info("Document file replaced successfully for ID: {}", updatedDocument.getId());

        return mapToEmployeeDocumentResponse(updatedDocument);
    }

    /**
     * Get an employee document by ID.
     *
     * @param id the ID of the document to retrieve
     * @return the employee document response
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public EmployeeDocumentResponse getDocument(UUID id) {
        log.debug("Request to get document with ID: {}", id);

        return employeeDocumentRepository
            .findById(id)
            .map(this::mapToEmployeeDocumentResponse)
            .orElseThrow(() -> new EntityNotFoundException("Document not found with ID: " + id));
    }

    /**
     * Get document content as a stream.
     *
     * @param id the ID of the document to retrieve
     * @return input stream of the document content
     * @throws IOException if an I/O error occurs
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public InputStream getDocumentContent(UUID id) throws IOException {
        log.debug("Request to get document content for ID: {}", id);

        EmployeeDocument document = employeeDocumentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Document not found with ID: " + id));

        return fileStorageService()
            .retrieve(document.getFilePath())
            .orElseThrow(() -> new IOException("Document content not found for ID: " + id));
    }

    /**
     * Get all documents for a specific employee.
     *
     * @param employeeId the ID of the employee
     * @return list of employee document responses
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public List<EmployeeDocumentResponse> getDocumentsByEmployee(UUID employeeId) {
        log.debug("Request to get all documents for Employee ID: {}", employeeId);

        // First verify that the employee exists
        if (!employeeRepository.existsById(employeeId)) {
            throw new EntityNotFoundException("Employee not found with ID: " + employeeId);
        }

        return employeeDocumentRepository
            .findAll()
            .stream()
            .filter(doc -> doc.getEmployee().getId().equals(employeeId))
            .map(this::mapToEmployeeDocumentResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get all documents with pagination.
     *
     * @param pageable pagination information
     * @return page of employee document responses
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<EmployeeDocumentResponse> getAllDocuments(Pageable pageable) {
        log.debug("Request to get all documents with pagination");

        return employeeDocumentRepository.findAll(pageable).map(this::mapToEmployeeDocumentResponse);
    }

    /**
     * Delete an employee document.
     *
     * @param id the ID of the document to delete
     */
    @Transactional("tenantTransactionManager")
    public void deleteDocument(UUID id) {
        log.debug("Request to delete document with ID: {}", id);

        employeeDocumentRepository
            .findById(id)
            .ifPresentOrElse(
                document -> {
                    // Delete the physical file using the storage service
                    if (fileStorageService().exists(document.getFilePath())) {
                        fileStorageService().delete(document.getFilePath());
                    }

                    // Delete the document record
                    employeeDocumentRepository.delete(document);
                    log.info("Document deleted successfully with ID: {}", id);
                },
                () -> {
                    throw new EntityNotFoundException("Document not found with ID: " + id);
                }
            );
    }

    /**
     * Maps an employee document entity to a response DTO.
     *
     * @param document the employee document entity
     * @return the employee document response
     */
    private EmployeeDocumentResponse mapToEmployeeDocumentResponse(EmployeeDocument document) {
        // Try to get a public URL if available
        String filePath = document.getFilePath();
        String publicUrl = fileStorageService().getUrl(filePath).orElse(filePath);

        return new EmployeeDocumentResponse(
            document.getId(),
            document.getType(),
            publicUrl,
            document.getOriginalFileName() != null ? document.getOriginalFileName() : storedName(document.getFilePath()),
            document.getContentType(),
            document.getSizeBytes(),
            document.getEmployee().getId(),
            document.getEmployee().getFirstName() + " " + document.getEmployee().getLastName(),
            document.getCreatedBy(),
            document.getCreatedDate(),
            document.getLastModifiedBy(),
            document.getLastModifiedDate()
        );
    }

    /** Accepted uploads: PDF and images, up to 20 MB (the EMPLOYEE_DOCUMENT file policy). */
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long MAX_BYTES = 20L * 1024 * 1024;

    private static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new com.humano.service.errors.BusinessRuleViolationException("document.empty", "The file is empty.");
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new com.humano.service.errors.BusinessRuleViolationException(
                "document.type",
                "Upload a PDF or an image (JPEG, PNG, WebP)."
            );
        }
        if (file.getSize() > MAX_BYTES) {
            throw new com.humano.service.errors.BusinessRuleViolationException("document.size", "Files can be at most 20 MB.");
        }
    }

    /** Stores the file under a generated key: {@code <employee>-<type>-<timestamp><.ext>}. */
    private String storeFile(UUID employeeId, String type, MultipartFile file) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String original = file.getOriginalFilename();
        int dot = original == null ? -1 : original.lastIndexOf('.');
        String extension = dot >= 0 ? original.substring(dot).replaceAll("[^A-Za-z0-9.]", "") : "";
        String slug = type.replaceAll("[^A-Za-z0-9]+", "-");
        String fileName = String.format("%s-%s-%s%s", employeeId, slug, timestamp, extension);
        return fileStorageService().store(file, "employee-documents/" + employeeId, fileName);
    }

    private static void applyFileMetadata(EmployeeDocument document, MultipartFile file) {
        String original = file.getOriginalFilename();
        document.setOriginalFileName(
            original == null || original.isBlank() ? null : java.nio.file.Paths.get(original).getFileName().toString()
        );
        document.setContentType(file.getContentType());
        document.setSizeBytes(file.getSize());
    }

    private static String storedName(String key) {
        return key == null ? null : key.substring(key.lastIndexOf('/') + 1);
    }
}
