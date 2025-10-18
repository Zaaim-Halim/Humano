package com.humano.service.storage;

import com.humano.security.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.support.SqlLobValue;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of FileStorageService that stores files directly in a database.
 * Supports tenant-specific storage isolation.
 */
@Component
public class DatabaseStorageService implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseStorageService.class);

    private final JdbcTemplate jdbcTemplate;
    private final LobHandler lobHandler;

    public DatabaseStorageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.lobHandler = new DefaultLobHandler();
        // Create table if it doesn't exist
        createTableIfNotExists();
        log.info("Database storage service initialized");
    }

    private void createTableIfNotExists() {
        try {
            // First check if the table exists to avoid errors
            boolean tableExists = false;
            try {
                jdbcTemplate.queryForObject("SELECT 1 FROM file_storage WHERE 1=0", Integer.class);
                tableExists = true;
            } catch (Exception e) {
                // Table doesn't exist
            }

            if (!tableExists) {
                // Determine database type to use appropriate SQL
                String dbProductName = jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName().toLowerCase();

                String createTableSql;
                if (dbProductName.contains("mysql") || dbProductName.contains("mariadb")) {
                    createTableSql =
                        "CREATE TABLE file_storage (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "tenant_id VARCHAR(255) NOT NULL, " +
                        "filename VARCHAR(255) NOT NULL, " +
                        "content_type VARCHAR(255), " +
                        "directory VARCHAR(255), " +
                        "file_size BIGINT, " +
                        "content LONGBLOB, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                } else if (dbProductName.contains("postgresql")) {
                    createTableSql =
                        "CREATE TABLE file_storage (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "tenant_id VARCHAR(255) NOT NULL, " +
                        "filename VARCHAR(255) NOT NULL, " +
                        "content_type VARCHAR(255), " +
                        "directory VARCHAR(255), " +
                        "file_size BIGINT, " +
                        "content BYTEA, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                } else if (dbProductName.contains("h2")) {
                    createTableSql =
                        "CREATE TABLE file_storage (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "tenant_id VARCHAR(255) NOT NULL, " +
                        "filename VARCHAR(255) NOT NULL, " +
                        "content_type VARCHAR(255), " +
                        "directory VARCHAR(255), " +
                        "file_size BIGINT, " +
                        "content BLOB, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                } else if (dbProductName.contains("oracle")) {
                    createTableSql =
                        "CREATE TABLE file_storage (" +
                        "id VARCHAR2(255) PRIMARY KEY, " +
                        "tenant_id VARCHAR2(255) NOT NULL, " +
                        "filename VARCHAR2(255) NOT NULL, " +
                        "content_type VARCHAR2(255), " +
                        "directory VARCHAR2(255), " +
                        "file_size NUMBER, " +
                        "content BLOB, " +
                        "created_at TIMESTAMP DEFAULT SYSTIMESTAMP" +
                        ")";
                } else if (dbProductName.contains("sqlserver") || dbProductName.contains("microsoft")) {
                    createTableSql =
                        "CREATE TABLE file_storage (" +
                        "id VARCHAR(255) PRIMARY KEY, " +
                        "tenant_id VARCHAR(255) NOT NULL, " +
                        "filename VARCHAR(255) NOT NULL, " +
                        "content_type VARCHAR(255), " +
                        "directory VARCHAR(255), " +
                        "file_size BIGINT, " +
                        "content VARBINARY(MAX), " +
                        "created_at DATETIME DEFAULT GETDATE()" +
                        ")";
                } else {
                    throw new UnsupportedOperationException("Database type not supported: " + dbProductName);
                }

                jdbcTemplate.execute(createTableSql);
                log.info("File storage table created");
            }
        } catch (Exception e) {
            log.error("Error creating file storage table", e);
        }
    }

    /**
     * Get the current tenant ID from the TenantContextHolder.
     * Throws an exception if no tenant context is set.
     *
     * @return the current tenant ID
     * @throws IllegalStateException if no tenant context is set
     */
    private UUID getCurrentTenantId() {
        UUID tenantId = TenantContextHolder.getCurrentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found. Operations must be performed within a tenant context.");
        }
        return tenantId;
    }

    @Override
    public String store(MultipartFile file, String directory) throws IOException {
        UUID tenantId = getCurrentTenantId();
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO file_storage (id, tenant_id, filename, content_type, directory, file_size, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql,
                id,
                tenantId.toString(),
                file.getOriginalFilename(),
                file.getContentType(),
                directory,
                file.getSize(),
                new SqlLobValue(file.getInputStream(), (int) file.getSize(), lobHandler),
                LocalDateTime.now()
            );
            log.debug("File stored with ID: {}", id);
            return id;
        } catch (Exception e) {
            log.error("Error storing file", e);
            throw new IOException("Failed to store file", e);
        }
    }

    @Override
    public String store(MultipartFile file, String directory, String filename) throws IOException {
        UUID tenantId = getCurrentTenantId();
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO file_storage (id, tenant_id, filename, content_type, directory, file_size, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql,
                id,
                tenantId.toString(),
                filename,
                file.getContentType(),
                directory,
                file.getSize(),
                new SqlLobValue(file.getInputStream(), (int) file.getSize(), lobHandler),
                LocalDateTime.now()
            );
            log.debug("File stored with ID: {} and custom filename: {}", id, filename);
            return id;
        } catch (Exception e) {
            log.error("Error storing file with custom filename", e);
            throw new IOException("Failed to store file", e);
        }
    }

    @Override
    public String store(InputStream inputStream, String directory, String filename, String contentType) throws IOException {
        UUID tenantId = getCurrentTenantId();
        String id = UUID.randomUUID().toString();
        String sql = "INSERT INTO file_storage (id, tenant_id, filename, content_type, directory, file_size, content, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            // We need to load the input stream into memory to get its size
            // This could be inefficient for large files - consider alternatives for production use
            byte[] bytes = inputStream.readAllBytes();
            long fileSize = bytes.length;

            jdbcTemplate.update(sql,
                id,
                tenantId.toString(),
                filename,
                contentType,
                directory,
                fileSize,
                new SqlLobValue(new ByteArrayInputStream(bytes), (int) fileSize, lobHandler),
                LocalDateTime.now()
            );
            log.debug("File stored from input stream with ID: {}", id);
            return id;
        } catch (Exception e) {
            log.error("Error storing file from input stream", e);
            throw new IOException("Failed to store file from input stream", e);
        }
    }

    @Override
    public Optional<InputStream> retrieve(String fileReference) throws IOException {
        UUID tenantId = getCurrentTenantId();
        try {
            String sql = "SELECT content FROM file_storage WHERE id = ? AND tenant_id = ?";
            Blob blob = jdbcTemplate.queryForObject(sql, new Object[] { fileReference, tenantId.toString() }, Blob.class);

            if (blob != null) {
                return Optional.of(blob.getBinaryStream());
            } else {
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("SQL error while retrieving file", e);
            throw new IOException("Failed to retrieve file", e);
        } catch (Exception e) {
            log.error("Error retrieving file", e);
            return Optional.empty();
        }
    }

    @Override
    public boolean delete(String fileReference) {
        UUID tenantId = getCurrentTenantId();
        try {
            String sql = "DELETE FROM file_storage WHERE id = ? AND tenant_id = ?";
            int rowsAffected = jdbcTemplate.update(sql, fileReference, tenantId.toString());

            return rowsAffected > 0;
        } catch (Exception e) {
            log.error("Error deleting file", e);
            return false;
        }
    }

    @Override
    public boolean exists(String fileReference) {
        UUID tenantId = getCurrentTenantId();
        try {
            String sql = "SELECT COUNT(*) FROM file_storage WHERE id = ? AND tenant_id = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, fileReference, tenantId.toString());
            return count != null && count > 0;
        } catch (Exception e) {
            log.error("Error checking if file exists", e);
            return false;
        }
    }

    @Override
    public Optional<String> getUrl(String fileReference) {
        // Database storage doesn't provide direct URLs
        // This could be implemented by creating a controller endpoint that serves files by ID
        // For now, returning empty since we don't have direct URL access
        return Optional.empty();
    }

    /**
     * Get metadata for a stored file.
     *
     * This is a helper method not defined in the FileStorageService interface.
     *
     * @param fileId the file identifier
     * @return optional metadata if file exists
     */
    public Optional<Map<String, Object>> getMetadata(String fileId) {
        UUID tenantId = getCurrentTenantId();
        try {
            String sql = "SELECT filename, content_type, directory, file_size, created_at FROM file_storage WHERE id = ? AND tenant_id = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, fileId, tenantId.toString());
            return Optional.of(row);
        } catch (Exception e) {
            log.error("Error fetching file metadata", e);
            return Optional.empty();
        }
    }
}
