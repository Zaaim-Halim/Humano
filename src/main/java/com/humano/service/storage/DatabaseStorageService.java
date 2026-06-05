package com.humano.service.storage;

import com.humano.domain.storage.FileBlob;
import com.humano.repository.storage.FileBlobRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * DATABASE backend — stores bytes in the tenant DB's {@code file_blob} table via
 * {@link FileBlobRepository}.
 * <p>
 * Tenant isolation is provided by the routing data source (the JPA repo participates in the
 * current tenant's connection), so this class does not filter by {@code tenant_id} and does
 * not need a {@code TenantIdResolver}.
 * <p>
 * Returned {@code storageKey} is the {@link FileBlob#getId()} as a string — the matching
 * {@link com.humano.domain.storage.StoredFile} row uses this to find the bytes. Filename /
 * content-type / directory passed to the {@code store(...)} overloads are ignored here; that
 * metadata belongs on {@code StoredFile}, not on the blob row.
 * <p>
 * Instantiated by {@link StorageFactory}; intentionally not a Spring {@code @Component}.
 */
public class DatabaseStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseStorageService.class);

    private final FileBlobRepository fileBlobRepository;

    public DatabaseStorageService(FileBlobRepository fileBlobRepository) {
        this.fileBlobRepository = fileBlobRepository;
    }

    @Override
    public String store(MultipartFile file, String directory) throws IOException {
        return persist(file.getBytes());
    }

    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        return persist(file.getBytes());
    }

    @Override
    public String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException {
        return persist(inputStream.readAllBytes());
    }

    private String persist(byte[] bytes) {
        FileBlob blob = new FileBlob();
        blob.setContent(bytes);
        blob.setSizeBytes(bytes.length);
        FileBlob saved = fileBlobRepository.save(blob);
        log.debug("Stored file_blob id={} ({} bytes)", saved.getId(), saved.getSizeBytes());
        return saved.getId().toString();
    }

    @Override
    public Optional<InputStream> retrieve(String fileReference) {
        UUID id = parseId(fileReference);
        if (id == null) return Optional.empty();
        return fileBlobRepository.findById(id).map(b -> new ByteArrayInputStream(b.getContent()));
    }

    @Override
    public boolean delete(String fileReference) {
        UUID id = parseId(fileReference);
        if (id == null) return false;
        if (!fileBlobRepository.existsById(id)) return false;
        fileBlobRepository.deleteById(id);
        return true;
    }

    @Override
    public boolean exists(String fileReference) {
        UUID id = parseId(fileReference);
        return id != null && fileBlobRepository.existsById(id);
    }

    @Override
    public Optional<String> getUrl(String fileReference) {
        // DB-backed bytes have no native URL; a controller endpoint serves them by StoredFile id.
        return Optional.empty();
    }

    private static UUID parseId(String fileReference) {
        if (fileReference == null || fileReference.isBlank()) return null;
        try {
            return UUID.fromString(fileReference);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
