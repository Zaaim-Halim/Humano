package com.humano.service.multitenancy;

import com.humano.config.multitenancy.MultiTenantProperties;
import com.humano.config.multitenancy.TenantPasswordCipher;
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
    private final TenantPasswordCipher passwordCipher;

    public TenantDatabaseManager(MultiTenantProperties properties, TenantPasswordCipher passwordCipher) {
        this.properties = properties;
        this.passwordCipher = passwordCipher;
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

        // The tenant DB user is intentionally non-SUPER, but tenant migrations create triggers
        // (e.g. the append-only audit_event guards). With binary logging enabled MySQL rejects
        // CREATE TRIGGER from a non-SUPER user (error 1419) unless this server-global is set, so
        // enable it here over the privileged admin connection. Best-effort: on a managed MySQL where
        // even the admin lacks the privilege, the trigger changeset is failOnError=false and the app
        // falls back to ORM-level @Immutable enforcement.
        allowNonSuperTriggerCreation(jdbcTemplate);

        LOG.info("Successfully created database {} on {}:{}", dbConfig.getDbName(), dbConfig.getDbHost(), dbConfig.getDbPort());
    }

    /**
     * Sets {@code log_bin_trust_function_creators=1} server-globally so the non-SUPER tenant user can
     * create the append-only audit triggers under binary logging. Server-wide and persists until the
     * MySQL server restarts; for permanent effect set it in the server config. Best-effort — a failure
     * (admin also lacks the privilege on a managed MySQL) is logged, not thrown, and provisioning
     * continues with the trigger changeset degrading to ORM-only enforcement.
     */
    private void allowNonSuperTriggerCreation(JdbcTemplate jdbcTemplate) {
        try {
            jdbcTemplate.execute("SET GLOBAL log_bin_trust_function_creators = 1");
        } catch (Exception e) {
            LOG.warn(
                "Could not set log_bin_trust_function_creators=1 ({}). If the MySQL server has binary " +
                "logging on and the tenant user lacks SUPER, append-only audit triggers will be skipped; " +
                "set this variable in the server config to enable them.",
                e.getMessage()
            );
        }
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
        // dbConfig.getDbPassword() is the ciphertext stored in tenant_database_config.db_password;
        // MySQL needs the plaintext for CREATE USER ... IDENTIFIED BY.
        String password = passwordCipher.decrypt(dbConfig.getDbPassword());
        String database = dbConfig.getDbName();

        // Create user
        String createUserSql = String.format("CREATE USER IF NOT EXISTS '%s'@'%%' IDENTIFIED BY '%s'", username, password);
        jdbcTemplate.execute(createUserSql);

        // Re-sync the password. CREATE USER IF NOT EXISTS leaves a pre-existing user's password
        // untouched, so a user lingering from an earlier provisioning attempt (or after the config
        // row's password was regenerated) would keep a stale password while the Hikari pool decrypts
        // the *current* stored credential — yielding "Access denied (using password: YES)" at pool
        // init. ALTER USER forces the MySQL password to match what this run will actually connect with.
        String alterUserSql = String.format("ALTER USER '%s'@'%%' IDENTIFIED BY '%s'", username, password);
        jdbcTemplate.execute(alterUserSql);

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
