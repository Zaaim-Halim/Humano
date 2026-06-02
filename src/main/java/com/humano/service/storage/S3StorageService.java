package com.humano.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Placeholder implementation of FileStorageService for Amazon S3.
 * Full implementation to be added in the future.
 */
/**
 * Stub for S3 storage. Not yet implemented. Intentionally not a Spring
 * {@code @Component} — instantiated on demand via {@code StorageFactory} when a
 * tenant's storage config selects S3, at which point this implementation will
 * throw {@link StorageException}.
 */
public class S3StorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    // Constructor to be implemented later
    public S3StorageService() {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public String store(MultipartFile file, String directory) throws IOException {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public Optional<InputStream> retrieve(String fileReference) throws IOException {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public boolean delete(String fileReference) {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public boolean exists(String fileReference) {
        throw new StorageException("S3 storage is not implemented yet");
    }

    @Override
    public Optional<String> getUrl(String fileReference) {
        throw new StorageException("S3 storage is not implemented yet");
    }
}
