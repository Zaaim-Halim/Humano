package com.humano.service.billing.entitlement;

import com.humano.repository.hr.EmployeeDocumentRepository;
import com.humano.repository.storage.StoredFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The plan's storage limit, whatever provider holds the bytes (file system, S3, Azure, database).
 * Every upload records its size in the workspace's own database before the bytes reach a provider,
 * so usage is the sum of those sizes — provider-agnostic, cheap, and unchanged when the workspace
 * switches provider. Checked before anything is written.
 */
@Service
public class StorageQuotaService {

    /** The plan's storage limit is in GB. */
    public static final double BYTES_PER_GB = 1024d * 1024 * 1024;

    private final StoredFileRepository storedFileRepository;
    private final EmployeeDocumentRepository employeeDocumentRepository;
    private final EntitlementService entitlementService;

    public StorageQuotaService(
        StoredFileRepository storedFileRepository,
        EmployeeDocumentRepository employeeDocumentRepository,
        EntitlementService entitlementService
    ) {
        this.storedFileRepository = storedFileRepository;
        this.employeeDocumentRepository = employeeDocumentRepository;
        this.entitlementService = entitlementService;
    }

    /** Bytes the workspace holds: uploaded files and employee documents. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public long usedBytes() {
        return storedFileRepository.totalSizeBytes() + employeeDocumentRepository.totalSizeBytes();
    }

    /**
     * Refuses (403, plan limit) when adding {@code addingBytes} would go over the plan's storage. A
     * replacement passes the difference with the file it replaces (which can be negative: always fine).
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public void checkFits(long addingBytes) {
        if (addingBytes <= 0) {
            return;
        }
        entitlementService.checkWithin(FeatureCodes.STORAGE, (usedBytes() + addingBytes) / BYTES_PER_GB);
    }
}
