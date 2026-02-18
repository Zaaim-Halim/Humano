package com.humano.service.multitenancy;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.domain.tenant.TenantDatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

/**
 * Manages the lifecycle of tenant databases.
 * Creates and drops databases on specified database servers.
 *
 * @author Humano Team
 */
@Service
public class TenantDatabaseManager {

    private static final Logger LOG = LoggerFactory.getLogger(TenantDatabaseManager.class);

    private final MultiTenantProperties properties;

    public TenantDatabaseManager(MultiTenantProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a new database for the tenant on the specified server.
     *
     * @param dbConfig the database configuration
     */
    public void createDatabase(TenantDatabaseConfig dbConfig) {
        LOG.info("Creating database {} on server {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());

        JdbcTemplate jdbcTemplate = createAdminJdbcTemplate(dbConfig);

        // Create the database
        String createDbSql = String.format(
            "CREATE DATABASE IF NOT EXISTS `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci",
            dbConfig.getDbName()
        );
        jdbcTemplate.execute(createDbSql);

        // Create dedicated user for this tenant's database
        createDatabaseUser(jdbcTemplate, dbConfig);

        LOG.info("Successfully created database {} on {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());
    }

    /**
     * Drops a tenant's database if it exists.
     *
     * @param dbConfig the database configuration
     */
    public void dropDatabaseIfExists(TenantDatabaseConfig dbConfig) {
        LOG.warn("Dropping database {} on server {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());

        JdbcTemplate jdbcTemplate = createAdminJdbcTemplate(dbConfig);

        // Drop the user first
        try {
            String dropUserSql = String.format("DROP USER IF EXISTS '%s'@'%%'", dbConfig.getDbUsername());
            jdbcTemplate.execute(dropUserSql);
        } catch (Exception e) {
            LOG.warn("Could not drop user {}: {}", dbConfig.getDbUsername(), e.getMessage());
        }

        // Drop the database
        String dropDbSql = String.format("DROP DATABASE IF EXISTS `%s`", dbConfig.getDbName());
        jdbcTemplate.execute(dropDbSql);

        LOG.info("Successfully dropped database {}", dbConfig.getDbName());
    }

    /**
     * Creates a dedicated database user for the tenant with limited permissions.
     *
     * @param jdbcTemplate the JDBC template
     * @param dbConfig the database configuration
     */
    private void createDatabaseUser(JdbcTemplate jdbcTemplate, TenantDatabaseConfig dbConfig) {
        String username = dbConfig.getDbUsername();
        String password = dbConfig.getDbPassword();
        String database = dbConfig.getDbName();

        // Create user
        String createUserSql = String.format("CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s'", username, password);
        jdbcTemplate.execute(createUserSql);

        // Grant permissions only to this tenant's database
        String grantSql = String.format("GRANT ALL PRIVILEGES ON `%s`.* TO '%s'@'%%'", database, username);
        jdbcTemplate.execute(grantSql);

        // Apply the privileges
        jdbcTemplate.execute("FLUSH PRIVILEGES");

        LOG.info("Created database user {} with access to {}", username, database);
    }

    /**
     * Creates a JdbcTemplate with admin credentials to manage databases.
     *
     * @param dbConfig the database configuration
     * @return a JdbcTemplate with admin credentials
     */
    private JdbcTemplate createAdminJdbcTemplate(TenantDatabaseConfig dbConfig) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());

        // Connect to the server without specifying a database
        String jdbcUrl = String.format(
            "jdbc:mysql://%s:%d?%s",
            dbConfig.getDbHost(),
            dbConfig.getDbPort(),
            "useSSL=true&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        );
        dataSource.setUrl(jdbcUrl);

        // Use admin credentials (from environment/config)
        String adminUsername = System.getenv("DB_ADMIN_USERNAME");
        String adminPassword = System.getenv("DB_ADMIN_PASSWORD");

        // Fallback to root if env vars not set (for development only)
        dataSource.setUsername(adminUsername != null ? adminUsername : "root");
        dataSource.setPassword(adminPassword != null ? adminPassword : "");

        return new JdbcTemplate(dataSource);
    }

    /**
     * Checks if a database exists on the specified server.
     *
     * @param dbConfig the database configuration
     * @return true if the database exists
     */
    public boolean databaseExists(TenantDatabaseConfig dbConfig) {
        JdbcTemplate jdbcTemplate = createAdminJdbcTemplate(dbConfig);
        String checkSql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
        return !jdbcTemplate.queryForList(checkSql, dbConfig.getDbName()).isEmpty();
    }
}
