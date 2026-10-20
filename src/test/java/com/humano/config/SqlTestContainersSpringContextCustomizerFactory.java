package com.humano.config;

import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.testcontainers.containers.JdbcDatabaseContainer;
import tech.jhipster.config.JHipsterConstants;

public class SqlTestContainersSpringContextCustomizerFactory implements ContextCustomizerFactory {

    private Logger log = LoggerFactory.getLogger(SqlTestContainersSpringContextCustomizerFactory.class);

    private static SqlTestContainer prodTestContainer;

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return new ContextCustomizer() {
            @Override
            public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
                ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
                TestPropertyValues testValues = TestPropertyValues.empty();
                EmbeddedSQL sqlAnnotation = AnnotatedElementUtils.findMergedAnnotation(testClass, EmbeddedSQL.class);
                boolean usingTestProdProfile = Arrays.asList(context.getEnvironment().getActiveProfiles()).contains(
                    "test" + JHipsterConstants.SPRING_PROFILE_PRODUCTION
                );
                if (null != sqlAnnotation && usingTestProdProfile) {
                    log.debug("detected the EmbeddedSQL annotation on class {}", testClass.getName());
                    log.info("Warming up the sql database");
                    if (null == prodTestContainer) {
                        try {
                            Class<? extends SqlTestContainer> containerClass = (Class<? extends SqlTestContainer>) Class.forName(
                                this.getClass().getPackageName() + ".MysqlTestContainer"
                            );
                            prodTestContainer = beanFactory.createBean(containerClass);
                            beanFactory.registerSingleton(containerClass.getName(), prodTestContainer);
                            /**
                             * ((DefaultListableBeanFactory)beanFactory).registerDisposableBean(containerClass.getName(), prodTestContainer);
                             */
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    // The application builds its own pools for the master and the default tenant
                    // database (MultiTenantDataSourceConfig); plain spring.datasource.* is never read.
                    for (String pool : List.of("master", "default-tenant")) {
                        testValues = testValues.and(datasourceProperties(pool, prodTestContainer.getTestContainer()));
                    }
                }
                testValues.applyTo(context);
            }

            /** Pool settings as in application-prod.yml, against a database of the container's server. */
            private List<String> datasourceProperties(String pool, JdbcDatabaseContainer<?> container) {
                String database = "master".equals(pool) ? "humano_master" : "humano_tenant_default";
                String prefix = "spring.datasource." + pool + ".";
                return List.of(
                    prefix +
                    "url=jdbc:mysql://" +
                    container.getHost() +
                    ":" +
                    container.getFirstMappedPort() +
                    "/" +
                    database +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8&createDatabaseIfNotExist=true",
                    // Root, so the driver can create both databases.
                    prefix + "username=root",
                    prefix + "password=" + container.getPassword(),
                    prefix + "driver-class-name=com.mysql.cj.jdbc.Driver",
                    prefix + "hikari.auto-commit=false"
                );
            }

            @Override
            public int hashCode() {
                return SqlTestContainer.class.getName().hashCode();
            }

            @Override
            public boolean equals(Object obj) {
                return this.hashCode() == obj.hashCode();
            }
        };
    }
}
