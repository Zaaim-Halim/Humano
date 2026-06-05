package com.humano.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Placeholder implementation of FileStorageService for Azure Blob Storage.
 * Full implementation to be added in the future.
 */
/**
 * Stub for Azure Blob storage. Not yet implemented. Intentionally not a Spring
 * {@code @Component} — instantiated on demand via {@code StorageFactory} when a
 * tenant's storage config selects Azure, at which point this implementation will
 * throw {@link StorageException}.
 */
public class AzureBlobStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobStorageService.class);

    // Constructor to be implemented later
    public AzureBlobStorageService() {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public String store(MultipartFile file, String directory) throws IOException {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public Optional<InputStream> retrieve(String fileReference) throws IOException {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public boolean delete(String fileReference) {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public boolean exists(String fileReference) {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }

    @Override
    public Optional<String> getUrl(String fileReference) {
        throw new StorageException("Azure Blob storage is not implemented yet");
    }
}
