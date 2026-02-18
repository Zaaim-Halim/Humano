package com.humano.config.multitenancy;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * JPA Configuration for multiple databases (master and tenant).
 * Configures separate EntityManagerFactories and TransactionManagers.
 *
 * @author Humano Team
 */
@Configuration
public class MultiTenantJpaConfig {

    /**
     * Entity Manager Factory for MASTER database entities.
     * Handles tenant registry, billing, and subscription entities.
     *
     * @param dataSource the master data source
     * @return the entity manager factory for master database
     */
    @Primary
    @Bean(name = "masterEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(@Qualifier("masterDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.humano.domain.tenant", "com.humano.domain.billing");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setPersistenceUnitName("master");
        em.setJpaPropertyMap(jpaProperties());
        return em;
    }

    /**
     * Entity Manager Factory for TENANT database entities.
     * Handles User, HR, and Payroll entities in tenant-specific databases.
     *
     * @param dataSource the tenant routing data source
     * @return the entity manager factory for tenant databases
     */
    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory(@Qualifier("tenantDataSource") DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan(
            "com.humano.domain", // User, Authority
            "com.humano.domain.hr", // HR entities
            "com.humano.domain.payroll" // Payroll entities
        );
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        em.setPersistenceUnitName("tenant");
        em.setJpaPropertyMap(jpaProperties());
        return em;
    }

    /**
     * Transaction Manager for master database operations.
     *
     * @param emf the master entity manager factory
     * @return the transaction manager for master database
     */
    @Primary
    @Bean(name = "masterTransactionManager")
    public PlatformTransactionManager masterTransactionManager(
        @Qualifier("masterEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf
    ) {
        return new JpaTransactionManager(emf.getObject());
    }

    /**
     * Transaction Manager for tenant database operations.
     *
     * @param emf the tenant entity manager factory
     * @return the transaction manager for tenant databases
     */
    @Bean(name = "tenantTransactionManager")
    public PlatformTransactionManager tenantTransactionManager(
        @Qualifier("tenantEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf
    ) {
        return new JpaTransactionManager(emf.getObject());
    }

    /**
     * Common JPA/Hibernate properties for both master and tenant databases.
     *
     * @return map of JPA properties
     */
    private Map<String, Object> jpaProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", false);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.jdbc.time_zone", "UTC");
        props.put("hibernate.id.new_generator_mappings", true);
        return props;
    }
}

/**
 * Repository configuration for master database entities.
 * Scans tenant and billing repositories to use master EntityManagerFactory.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = { "com.humano.repository.tenant", "com.humano.repository.billing" },
    entityManagerFactoryRef = "masterEntityManagerFactory",
    transactionManagerRef = "masterTransactionManager"
)
class MasterRepositoryConfig {}

/**
 * Repository configuration for tenant database entities.
 * Scans HR and payroll repositories to use tenant EntityManagerFactory.
 */
@Configuration
@EnableJpaRepositories(
    basePackages = { "com.humano.repository.hr", "com.humano.repository.payroll" },
    entityManagerFactoryRef = "tenantEntityManagerFactory",
    transactionManagerRef = "tenantTransactionManager"
)
class TenantRepositoryConfig {}
