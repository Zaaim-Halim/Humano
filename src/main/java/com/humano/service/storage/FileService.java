package com.humano.service.storage;

import com.humano.config.multitenancy.TenantIdResolver;
import com.humano.domain.enumeration.storage.FileContext;
import com.humano.domain.enumeration.storage.FileVisibility;
import com.humano.domain.enumeration.storage.StorageBackendType;
import com.humano.domain.storage.StoredFile;
import com.humano.domain.tenant.TenantStorageConfig;
import com.humano.domain.tenant.storage.StorageConfigDetails;
import com.humano.repository.storage.StoredFileRepository;
import com.humano.repository.tenant.TenantStorageConfigRepository;
import com.humano.service.errors.EntityNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * High-level file API. Validates an upload against its {@link FileContext} and the active
 * {@link TenantStorageConfig}, writes bytes through the tenant's {@link FileStorageService}
 * backend, and persists a {@link StoredFile} metadata row that the rest of the platform
 * references.
 * <p>
 * The byte-handling {@link FileStorageService} stays a thin interface on purpose — it only
 * knows about bytes and {@code storageKey}s. Everything policy-shaped (size caps, MIME
 * allow-lists, visibility, ownership, soft-delete) lives here.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** Token bytes for {@link FileVisibility#PUBLIC} files; 192 bits URL-safe ≈ 32 chars. */
    private static final int PUBLIC_TOKEN_BYTES = 24;

    private final StoredFileRepository storedFileRepository;
    private final TenantStorageConfigRepository tenantStorageConfigRepository;
    private final TenantIdResolver tenantIdResolver;
    private final StorageFactory storageFactory;
    private final SecureRandom secureRandom = new SecureRandom();

    public FileService(
        StoredFileRepository storedFileRepository,
        TenantStorageConfigRepository tenantStorageConfigRepository,
        TenantIdResolver tenantIdResolver,
        StorageFactory storageFactory
    ) {
        this.storedFileRepository = storedFileRepository;
        this.tenantStorageConfigRepository = tenantStorageConfigRepository;
        this.tenantIdResolver = tenantIdResolver;
        this.storageFactory = storageFactory;
    }

    /**
     * Upload a file and create its {@link StoredFile} row.
     *
     * @param file       the multipart payload from the request.
     * @param context    drives validation policy (size cap, MIME allow-list, default visibility).
     * @param ownerType  lower-cased entity name, e.g. {@code "employee"}; {@code null} for tenant-owned.
     * @param ownerId    matching entity id; {@code null} for tenant-owned.
     * @param visibility explicit override; {@code null} uses {@link FileContext#defaultVisibility()}.
     * @param uploadedBy user id of the uploader; {@code null} for system uploads.
     */
    @Transactional("tenantTransactionManager")
    public StoredFile upload(
        MultipartFile file,
        FileContext context,
        String ownerType,
        UUID ownerId,
        FileVisibility visibility,
        UUID uploadedBy
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Uploaded file is empty");
        }
        if (context == null) {
            throw new StorageException("FileContext is required");
        }

        // Resolve the active config to determine the backend and its per-file cap.
        UUID tenantId = tenantIdResolver.requireCurrentTenantId();
        StorageConfigDetails activeDetails = tenantStorageConfigRepository
            .findByTenant_IdAndActiveTrue(tenantId)
            .map(TenantStorageConfig::getConfig)
            .orElse(null);

        validateContentType(file, context);
        long effectiveCap = effectiveCap(context, activeDetails);
        if (file.getSize() > effectiveCap) {
            throw new StorageException(
                "File of " + file.getSize() + " bytes exceeds cap of " + effectiveCap + " bytes for context " + context
            );
        }

        FileStorageService backend = storageFactory.getStorageService();
        StorageBackendType backendType = activeDetails != null ? activeDetails.type() : StorageBackendType.FILESYSTEM;

        // Hash before delegating so we can record the integrity checksum alongside bytes.
        byte[] bytes = file.getBytes();
        String sha256 = sha256Hex(bytes);

        String directory = directoryFor(context, ownerType, ownerId);
        String storageKey = backend.store(file, directory);

        FileVisibility effectiveVisibility = visibility != null ? visibility : context.defaultVisibility();

        StoredFile sf = new StoredFile();
        sf.setContext(context);
        sf.setOwnerType(ownerType);
        sf.setOwnerId(ownerId);
        sf.setOriginalFilename(safeFilename(file.getOriginalFilename()));
        sf.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        sf.setSizeBytes(file.getSize());
        sf.setChecksumSha256(sha256);
        sf.setVisibility(effectiveVisibility);
        sf.setBackend(backendType);
        sf.setStorageKey(storageKey);
        sf.setUploadedBy(uploadedBy);
        if (effectiveVisibility == FileVisibility.PUBLIC) {
            sf.setPublicToken(newPublicToken());
        }

        StoredFile saved = storedFileRepository.save(sf);
        log.info(
            "Uploaded file id={} context={} backend={} size={} owner={}/{}",
            saved.getId(),
            context,
            backendType,
            file.getSize(),
            ownerType,
            ownerId
        );
        return saved;
    }

    /**
     * Stream the bytes for a stored file. Returned record carries the {@link InputStream} along
     * with the original filename / content type so controllers can set headers without
     * re-querying.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public FileDownload download(UUID storedFileId) throws IOException {
        StoredFile sf = storedFileRepository
            .findById(storedFileId)
            .orElseThrow(() -> EntityNotFoundException.create("StoredFile", storedFileId));
        if (sf.isDeleted()) {
            throw EntityNotFoundException.create("StoredFile", storedFileId);
        }
        InputStream in = storageFactory
            .getStorageService()
            .retrieve(sf.getStorageKey())
            .orElseThrow(() -> new StorageException("Backend missing bytes for StoredFile " + storedFileId));
        return new FileDownload(in, sf.getContentType(), sf.getOriginalFilename(), sf.getSizeBytes());
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Optional<StoredFile> findByPublicToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return storedFileRepository.findByPublicToken(token).filter(sf -> !sf.isDeleted());
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<StoredFile> listByOwner(String ownerType, UUID ownerId, Pageable pageable) {
        return storedFileRepository.findByOwnerTypeAndOwnerIdAndDeletedAtIsNull(ownerType, ownerId, pageable);
    }

    /**
     * Soft-delete: marks {@code deleted_at}. Backend bytes are kept until the retention worker
     * hard-deletes them — gives operators a grace period to undo.
     */
    @Transactional("tenantTransactionManager")
    public void softDelete(UUID storedFileId) {
        StoredFile sf = storedFileRepository
            .findById(storedFileId)
            .orElseThrow(() -> EntityNotFoundException.create("StoredFile", storedFileId));
        if (sf.isDeleted()) return;
        sf.setDeletedAt(Instant.now());
        storedFileRepository.save(sf);
    }

    // ---- validation helpers ------------------------------------------------------------

    private static void validateContentType(MultipartFile file, FileContext context) {
        String declared = file.getContentType();
        if (!context.isContentTypeAllowed(declared)) {
            throw new StorageException("Content type '" + declared + "' not allowed for context " + context);
        }
    }

    private static long effectiveCap(FileContext context, StorageConfigDetails details) {
        long contextCap = context.maxBytes();
        long backendCap = details != null ? details.maxFileSizeBytes() : Long.MAX_VALUE;
        return Math.min(contextCap, backendCap);
    }

    private static String directoryFor(FileContext context, String ownerType, UUID ownerId) {
        StringBuilder sb = new StringBuilder(context.name().toLowerCase());
        if (ownerType != null) sb.append('/').append(ownerType);
        if (ownerId != null) sb.append('/').append(ownerId);
        return sb.toString();
    }

    private static String safeFilename(String original) {
        if (original == null || original.isBlank()) return "file";
        // Strip any path separators the client may have sent.
        String base = original.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        return slash >= 0 ? base.substring(slash + 1) : base;
    }

    private String newPublicToken() {
        byte[] buf = new byte[PUBLIC_TOKEN_BYTES];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by every JCA provider; absence is a JVM bug.
            throw new StorageException("SHA-256 unavailable", e);
        }
    }

    /** Streamable result of {@link #download(UUID)}. */
    public record FileDownload(InputStream content, String contentType, String filename, long sizeBytes) {}
}
