package com.humano.config;

import java.util.concurrent.Executor;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tech.jhipster.config.JHipsterConstants;

@Configuration
public class LiquibaseConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseConfiguration.class);

    private final Environment env;

    public LiquibaseConfiguration(Environment env) {
        this.env = env;
    }

    @Bean
    @DependsOn("masterDataSource")
    public SpringLiquibase masterLiquibase(
        @Qualifier("taskExecutor") Executor executor,
        @Qualifier("masterDataSource") DataSource masterDataSource,
        ApplicationProperties applicationProperties,
        LiquibaseProperties liquibaseProperties
    ) {
        SpringLiquibase liquibase = createSpringLiquibase(executor, masterDataSource, applicationProperties);
        liquibase.setChangeLog("classpath:config/liquibase/master.xml");
        configureLiquibase(liquibase, liquibaseProperties, "master");
        return liquibase;
    }

    @Bean
    @DependsOn({ "masterLiquibase", "defaultTenantDataSource" })
    public SpringLiquibase tenantLiquibase(
        @Qualifier("taskExecutor") Executor executor,
        @Qualifier("defaultTenantDataSource") DataSource defaultTenantDataSource,
        ApplicationProperties applicationProperties,
        LiquibaseProperties liquibaseProperties
    ) {
        SpringLiquibase liquibase = createSpringLiquibase(executor, defaultTenantDataSource, applicationProperties);
        liquibase.setChangeLog("classpath:config/liquibase/tenant.xml");
        configureLiquibase(liquibase, liquibaseProperties, "tenant");
        return liquibase;
    }

    private SpringLiquibase createSpringLiquibase(Executor executor, DataSource dataSource, ApplicationProperties applicationProperties) {
        SpringLiquibase liquibase;
        if (Boolean.TRUE.equals(applicationProperties.getLiquibase().getAsyncStart())) {
            liquibase = new AsyncSpringLiquibase(executor, env);
            LOG.debug("Configuring Liquibase with async start");
        } else {
            liquibase = new SpringLiquibase();
        }
        liquibase.setDataSource(dataSource);
        return liquibase;
    }

    private void configureLiquibase(SpringLiquibase liquibase, LiquibaseProperties liquibaseProperties, String name) {
        if (!CollectionUtils.isEmpty(liquibaseProperties.getContexts())) {
            liquibase.setContexts(StringUtils.collectionToCommaDelimitedString(liquibaseProperties.getContexts()));
        }
        liquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());
        liquibase.setLiquibaseSchema(liquibaseProperties.getLiquibaseSchema());
        liquibase.setLiquibaseTablespace(liquibaseProperties.getLiquibaseTablespace());
        liquibase.setDatabaseChangeLogLockTable(liquibaseProperties.getDatabaseChangeLogLockTable());
        liquibase.setDatabaseChangeLogTable(liquibaseProperties.getDatabaseChangeLogTable());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        if (!CollectionUtils.isEmpty(liquibaseProperties.getLabelFilter())) {
            liquibase.setLabelFilter(StringUtils.collectionToCommaDelimitedString(liquibaseProperties.getLabelFilter()));
        }
        liquibase.setChangeLogParameters(liquibaseProperties.getParameters());
        liquibase.setRollbackFile(liquibaseProperties.getRollbackFile());
        liquibase.setTestRollbackOnUpdate(liquibaseProperties.isTestRollbackOnUpdate());
        if (env.matchesProfiles(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE)) {
            liquibase.setShouldRun(false);
        } else {
            liquibase.setShouldRun(liquibaseProperties.isEnabled());
            LOG.debug("Configuring Liquibase for {}", name);
        }
    }

    /**
     * Async version of SpringLiquibase that runs migrations in a separate thread.
     */
    private static class AsyncSpringLiquibase extends SpringLiquibase {

        private final Executor executor;
        private final Environment env;

        AsyncSpringLiquibase(Executor executor, Environment env) {
            this.executor = executor;
            this.env = env;
        }

        @Override
        public void afterPropertiesSet() {
            if (!env.acceptsProfiles(org.springframework.core.env.Profiles.of(JHipsterConstants.SPRING_PROFILE_NO_LIQUIBASE))) {
                executor.execute(() -> {
                    try {
                        LOG.info("Starting Liquibase asynchronously...");
                        super.afterPropertiesSet();
                        LOG.info("Liquibase has updated your database");
                    } catch (Exception e) {
                        LOG.error("Liquibase could not start correctly: {}", e.getMessage(), e);
                    }
                });
            }
        }
    }
}
