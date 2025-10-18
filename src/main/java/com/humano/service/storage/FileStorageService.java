package com.humano.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface for file storage operations.
 * Provides abstraction for different storage implementations (filesystem, S3, database, etc.).
 */
public interface FileStorageService {

    /**
     * Store a file in the storage system.
     *
     * @param file the file to store
     * @param directory optional subdirectory path
     * @return the file reference (path, URL, or identifier)
     * @throws IOException if an I/O error occurs
     */
    String store(MultipartFile file, String directory) throws IOException;

    /**
     * Store a file in the storage system with a specific name.
     *
     * @param file the file to store
     * @param directory optional subdirectory path
     * @param filename the name to use for the stored file
     * @return the file reference (path, URL, or identifier)
     * @throws IOException if an I/O error occurs
     */
    String store(MultipartFile file, String directory, String filename) throws IOException;

    /**
     * Store content from an input stream.
     *
     * @param inputStream the input stream containing the file data
     * @param directory optional subdirectory path
     * @param filename the name to use for the stored file
     * @param contentType the content type of the file
     * @return the file reference (path, URL, or identifier)
     * @throws IOException if an I/O error occurs
     */
    String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException;

    /**
     * Load a file as a resource.
     *
     * @param fileReference the file reference (path, URL, or identifier)
     * @return optional input stream to the file
     * @throws IOException if an I/O error occurs
     */
    Optional<InputStream> retrieve(String fileReference) throws IOException;

    /**
     * Delete a file from the storage.
     *
     * @param fileReference the file reference (path, URL, or identifier)
     * @return true if deleted successfully, false otherwise
     */
    boolean delete(String fileReference);

    /**
     * Check if a file exists.
     *
     * @param fileReference the file reference (path, URL, or identifier)
     * @return true if the file exists, false otherwise
     */
    boolean exists(String fileReference);

    /**
     * Get the public URL for a file (if supported by the implementation).
     *
     * @param fileReference the file reference (path, URL, or identifier)
     * @return the public URL to access the file
     */
    Optional<String> getUrl(String fileReference);
}
