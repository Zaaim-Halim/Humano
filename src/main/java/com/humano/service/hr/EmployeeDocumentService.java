package com.humano.service.hr;

import com.humano.domain.hr.EmployeeDocument;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreateEmployeeDocumentRequest;
import com.humano.dto.hr.requests.UpdateEmployeeDocumentRequest;
import com.humano.dto.hr.responses.EmployeeDocumentResponse;
import com.humano.repository.hr.EmployeeDocumentRepository;
import com.humano.repository.shared.EmployeeRepository;
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
    private final FileStorageService fileStorageService;

    public EmployeeDocumentService(
        EmployeeDocumentRepository employeeDocumentRepository,
        EmployeeRepository employeeRepository,
        StorageFactory storageFactory
    ) {
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.employeeRepository = employeeRepository;
        this.fileStorageService = storageFactory.getStorageService();
        log.info("Employee document service initialized with storage provider: {}", fileStorageService.getClass().getSimpleName());
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
    @Transactional
    public EmployeeDocumentResponse uploadDocument(UUID employeeId, CreateEmployeeDocumentRequest request, MultipartFile file)
        throws IOException {
        log.debug("Request to upload document for Employee ID: {}", employeeId);

        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> new EntityNotFoundException("Employee not found with ID: " + employeeId));

        // Generate unique file name
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = String.format("%s-%s-%s%s", employee.getId(), request.type().replaceAll("\\s+", "-"), timestamp, extension);

        // Store the file using the storage service
        String directory = "employee-documents/" + employee.getId();
        String fileReference = fileStorageService.store(file, directory, fileName);

        // Create document entity
        EmployeeDocument document = new EmployeeDocument();
        document.setEmployee(employee);
        document.setType(request.type());
        document.setFilePath(fileReference);

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
    @Transactional
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
    @Transactional
    public EmployeeDocumentResponse replaceDocumentFile(UUID id, MultipartFile file) throws IOException {
        log.debug("Request to replace file for document with ID: {}", id);

        EmployeeDocument document = employeeDocumentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Document not found with ID: " + id));

        // Generate new unique file name
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String fileName = String.format(
            "%s-%s-%s%s",
            document.getEmployee().getId(),
            document.getType().replaceAll("\\s+", "-"),
            timestamp,
            extension
        );

        // Store the new file
        String directory = "employee-documents/" + document.getEmployee().getId();
        String fileReference = fileStorageService.store(file, directory, fileName);

        // Delete the old file if possible
        if (fileStorageService.exists(document.getFilePath())) {
            fileStorageService.delete(document.getFilePath());
        }

        // Update document entity
        document.setFilePath(fileReference);

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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public InputStream getDocumentContent(UUID id) throws IOException {
        log.debug("Request to get document content for ID: {}", id);

        EmployeeDocument document = employeeDocumentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Document not found with ID: " + id));

        return fileStorageService
            .retrieve(document.getFilePath())
            .orElseThrow(() -> new IOException("Document content not found for ID: " + id));
    }

    /**
     * Get all documents for a specific employee.
     *
     * @param employeeId the ID of the employee
     * @return list of employee document responses
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Page<EmployeeDocumentResponse> getAllDocuments(Pageable pageable) {
        log.debug("Request to get all documents with pagination");

        return employeeDocumentRepository.findAll(pageable).map(this::mapToEmployeeDocumentResponse);
    }

    /**
     * Delete an employee document.
     *
     * @param id the ID of the document to delete
     */
    @Transactional
    public void deleteDocument(UUID id) {
        log.debug("Request to delete document with ID: {}", id);

        employeeDocumentRepository
            .findById(id)
            .ifPresentOrElse(
                document -> {
                    // Delete the physical file using the storage service
                    if (fileStorageService.exists(document.getFilePath())) {
                        fileStorageService.delete(document.getFilePath());
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
        String publicUrl = fileStorageService.getUrl(filePath).orElse(filePath);

        return new EmployeeDocumentResponse(
            document.getId(),
            document.getType(),
            publicUrl,
            document.getEmployee().getId(),
            document.getEmployee().getFirstName() + " " + document.getEmployee().getLastName(),
            document.getCreatedBy(),
            document.getCreatedDate(),
            document.getLastModifiedBy(),
            document.getLastModifiedDate()
        );
    }
}
