package com.humano.config;

import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Leaves actuator endpoints out of the API docs when they run on their own management port.
 *
 * <p>With {@code springdoc.show-actuator: true} and a separate {@code management.server.port},
 * springdoc registers a bean factory post-processor that needs every {@code GroupedOpenApi}
 * before configuration properties are bound. JHipster's group configuration can't be built that
 * early, so the application failed to start. On a separate port the actuator endpoints aren't
 * served from the documented API anyway, so documenting them is switched off; on the same port
 * (the default) nothing changes.
 */
public class SpringDocManagementPortEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SHOW_ACTUATOR = "springdoc.show-actuator";
    private static final String SOURCE_NAME = "humanoSpringDocManagementPort";

    private final Log log;

    public SpringDocManagementPortEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(SpringDocManagementPortEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean showActuator = environment.getProperty(SHOW_ACTUATOR, Boolean.class, false);
        if (showActuator && ManagementPortType.get(environment) == ManagementPortType.DIFFERENT) {
            environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, Map.of(SHOW_ACTUATOR, "false")));
            log.info("Actuator endpoints run on a separate management port: leaving them out of the API docs");
        }
    }

    /** After the config files are loaded, so the ports they set are seen. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
