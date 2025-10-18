package com.humano.service.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of FileStorageService that stores files in the local filesystem.
 */
@Component
public class FilesystemStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FilesystemStorageService.class);

    private final Path rootLocation;

    /**
     * Create a new FilesystemStorageService with the given root location.
     * This constructor can be called programmatically with a specific path for each tenant.
     *
     * @param rootLocation the root storage location path
     */
    public FilesystemStorageService(Path rootLocation) {
        this.rootLocation = rootLocation.toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.rootLocation);
            log.info("Initialized file storage at: {}", this.rootLocation);
        } catch (IOException e) {
            log.error("Could not initialize storage location: {}", rootLocation, e);
            throw new StorageException("Could not initialize storage location: " + rootLocation, e);
        }
    }

    @Override
    public String store(MultipartFile file, String directory) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String originalFilename = file.getOriginalFilename();
        String filename = UUID.randomUUID().toString() + "-" + timestamp + getExtension(originalFilename);

        return store(file, directory, filename);
    }

    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file " + filename);
        }

        Path targetDir = createDirectoryIfNeeded(directory);
        Path destinationFile = targetDir.resolve(Paths.get(filename))
                .normalize().toAbsolutePath();

        if (!destinationFile.getParent().startsWith(this.rootLocation)) {
            // This is a security check to prevent storing files outside the root location
            throw new StorageException("Cannot store file outside current directory.");
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }

        log.debug("Stored file {} in filesystem", destinationFile);
        return destinationFile.toString();
    }

    @Override
    public String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException {
        Path targetDir = createDirectoryIfNeeded(directory);
        Path destinationFile = targetDir.resolve(Paths.get(filename))
                .normalize().toAbsolutePath();

        if (!destinationFile.getParent().startsWith(this.rootLocation)) {
            throw new StorageException("Cannot store file outside current directory.");
        }

        Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored file {} in filesystem", destinationFile);

        return destinationFile.toString();
    }

    @Override
    public Optional<InputStream> retrieve(String fileReference) throws IOException {
        Path filePath = Paths.get(fileReference);

        if (Files.exists(filePath)) {
            return Optional.of(Files.newInputStream(filePath));
        }

        return Optional.empty();
    }

    @Override
    public boolean delete(String fileReference) {
        try {
            Path filePath = Paths.get(fileReference);
            return Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Error deleting file: {}", fileReference, e);
            return false;
        }
    }

    @Override
    public boolean exists(String fileReference) {
        Path filePath = Paths.get(fileReference);
        return Files.exists(filePath);
    }

    @Override
    public Optional<String> getUrl(String fileReference) {
        // For filesystem storage, URLs are not directly available
        // You would typically need to expose these through a web server
        return Optional.empty();
    }

    private Path createDirectoryIfNeeded(String directory) throws IOException {
        Path targetDir = directory != null && !directory.isBlank()
                ? this.rootLocation.resolve(directory)
                : this.rootLocation;

        Files.createDirectories(targetDir);
        return targetDir;
    }

    private String getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(f.lastIndexOf(".")))
                .orElse("");
    }
}
