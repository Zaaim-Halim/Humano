package com.humano.service.storage;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Filesystem-backed storage. Instantiated per tenant by {@link StorageFactory} with the
 * tenant's configured root location, so this is intentionally not a Spring {@code @Component}.
 * <p>
 * {@code storageKey} contract: the {@code store(...)} methods return a path
 * <strong>relative</strong> to {@link #rootLocation} so the key survives root moves and stays
 * meaningful in {@link com.humano.domain.storage.StoredFile#getStorageKey()}. All reads / deletes
 * resolve the relative key against the current root.
 */
public class FilesystemStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FilesystemStorageService.class);

    private final Path rootLocation;

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
        String filename = UUID.randomUUID() + "-" + timestamp + getExtension(file.getOriginalFilename());
        return store(file, directory, filename);
    }

    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        if (file.isEmpty()) {
            throw new StorageException("Failed to store empty file " + filename);
        }
        Path destination = resolveSafe(directory, filename);
        Files.createDirectories(destination.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return toRelativeKey(destination);
    }

    @Override
    public String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException {
        Path destination = resolveSafe(directory, filename);
        Files.createDirectories(destination.getParent());
        Files.copy(inputStream, destination, StandardCopyOption.REPLACE_EXISTING);
        return toRelativeKey(destination);
    }

    @Override
    public Optional<InputStream> retrieve(String fileReference) throws IOException {
        Path filePath = resolveKey(fileReference);
        return Files.exists(filePath) ? Optional.of(Files.newInputStream(filePath)) : Optional.empty();
    }

    @Override
    public boolean delete(String fileReference) {
        try {
            return Files.deleteIfExists(resolveKey(fileReference));
        } catch (IOException e) {
            log.error("Error deleting file: {}", fileReference, e);
            return false;
        }
    }

    @Override
    public boolean exists(String fileReference) {
        return Files.exists(resolveKey(fileReference));
    }

    @Override
    public Optional<String> getUrl(String fileReference) {
        // Filesystem has no native URL; a controller endpoint serves bytes by StoredFile id.
        return Optional.empty();
    }

    /** Resolve {@code directory}+{@code filename} under {@link #rootLocation} with a traversal guard. */
    private Path resolveSafe(String directory, String filename) {
        Path base = (directory != null && !directory.isBlank()) ? this.rootLocation.resolve(directory) : this.rootLocation;
        Path target = base.resolve(Paths.get(filename)).normalize().toAbsolutePath();
        if (!target.startsWith(this.rootLocation)) {
            throw new StorageException("Cannot store file outside root location: " + filename);
        }
        return target;
    }

    /**
     * Resolve a stored relative key back to an absolute path. Accepts legacy absolute paths too
     * (returned by older versions of this service) for forward compatibility; new writes always
     * persist relative keys.
     */
    private Path resolveKey(String key) {
        Path p = Paths.get(key);
        Path resolved = (p.isAbsolute() ? p : this.rootLocation.resolve(p)).normalize();
        if (!resolved.startsWith(this.rootLocation)) {
            throw new StorageException("Resolved path escapes root location: " + key);
        }
        return resolved;
    }

    private String toRelativeKey(Path absolute) {
        return this.rootLocation.relativize(absolute).toString();
    }

    private String getExtension(String filename) {
        return Optional.ofNullable(filename).filter(f -> f.contains(".")).map(f -> f.substring(f.lastIndexOf("."))).orElse("");
    }
}
