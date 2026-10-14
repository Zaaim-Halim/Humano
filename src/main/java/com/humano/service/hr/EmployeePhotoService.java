package com.humano.service.hr;

import com.humano.domain.enumeration.storage.FileContext;
import com.humano.domain.shared.Employee;
import com.humano.domain.storage.StoredFile;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.repository.storage.StoredFileRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.storage.FileService;
import com.humano.service.storage.FileService.FileDownload;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Employee profile photos. The image is stored through {@link FileService} under the
 * {@link FileContext#EMPLOYEE_AVATAR} policy (JPEG/PNG/WebP, 2&nbsp;MiB) and the employee's
 * {@code imageUrl} points at {@code /api/hr/employees/{id}/photo/{fileId}} — the file id in the
 * URL changes with every upload, so browsers never show a stale cached photo.
 */
@Service
public class EmployeePhotoService {

    private static final Logger log = LoggerFactory.getLogger(EmployeePhotoService.class);
    private static final String OWNER_TYPE = "employee";

    private final EmployeeRepository employeeRepository;
    private final StoredFileRepository storedFileRepository;
    private final FileService fileService;

    public EmployeePhotoService(EmployeeRepository employeeRepository, StoredFileRepository storedFileRepository, FileService fileService) {
        this.employeeRepository = employeeRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileService = fileService;
    }

    /** Stores a new photo, replaces the previous one and returns the new {@code imageUrl}. */
    @Transactional("tenantTransactionManager")
    public String upload(UUID employeeId, MultipartFile file, UUID uploadedBy) throws IOException {
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
        UUID previous = currentFileId(employee);
        StoredFile stored = fileService.upload(file, FileContext.EMPLOYEE_AVATAR, OWNER_TYPE, employeeId, null, uploadedBy);
        String imageUrl = "/api/hr/employees/" + employeeId + "/photo/" + stored.getId();
        employee.setImageUrl(imageUrl);
        employeeRepository.save(employee);
        if (previous != null) {
            fileService.softDelete(previous);
        }
        log.info("Updated photo of employee {} (file {})", employeeId, stored.getId());
        return imageUrl;
    }

    /** Removes the employee's photo (initials are shown instead). */
    @Transactional("tenantTransactionManager")
    public void remove(UUID employeeId) {
        Employee employee = employeeRepository
            .findById(employeeId)
            .orElseThrow(() -> EntityNotFoundException.create("Employee", employeeId));
        UUID previous = currentFileId(employee);
        employee.setImageUrl(null);
        employeeRepository.save(employee);
        if (previous != null) {
            fileService.softDelete(previous);
        }
    }

    /** The photo bytes, only if {@code fileId} really is this employee's avatar. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public FileDownload download(UUID employeeId, UUID fileId) throws IOException {
        StoredFile sf = storedFileRepository.findById(fileId).orElseThrow(() -> EntityNotFoundException.create("StoredFile", fileId));
        if (
            sf.getContext() != FileContext.EMPLOYEE_AVATAR || !OWNER_TYPE.equals(sf.getOwnerType()) || !employeeId.equals(sf.getOwnerId())
        ) {
            throw EntityNotFoundException.create("StoredFile", fileId);
        }
        return fileService.download(fileId);
    }

    private UUID currentFileId(Employee employee) {
        String url = employee.getImageUrl();
        String marker = "/photo/";
        if (url == null || !url.contains(marker)) {
            return null;
        }
        try {
            return UUID.fromString(url.substring(url.lastIndexOf(marker) + marker.length()));
        } catch (IllegalArgumentException e) {
            return null; // an external image URL, not one of ours
        }
    }
}
