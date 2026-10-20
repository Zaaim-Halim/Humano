package com.humano.config;

import static com.humano.config.SpringDocManagementPortEnvironmentPostProcessor.SHOW_ACTUATOR;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.mock.env.MockEnvironment;

class SpringDocManagementPortEnvironmentPostProcessorTest {

    private final SpringDocManagementPortEnvironmentPostProcessor processor = new SpringDocManagementPortEnvironmentPostProcessor(
        new DeferredLogs()
    );

    private String showActuatorAfter(MockEnvironment env) {
        processor.postProcessEnvironment(env, new SpringApplication());
        return env.getProperty(SHOW_ACTUATOR);
    }

    @Test
    void keepsActuatorDocsOnTheSamePort() {
        assertThat(showActuatorAfter(new MockEnvironment().withProperty(SHOW_ACTUATOR, "true"))).isEqualTo("true");
        assertThat(
            showActuatorAfter(
                new MockEnvironment()
                    .withProperty(SHOW_ACTUATOR, "true")
                    .withProperty("server.port", "8080")
                    .withProperty("management.server.port", "8080")
            )
        ).isEqualTo("true");
    }

    @Test
    void dropsActuatorDocsOnASeparateManagementPort() {
        assertThat(
            showActuatorAfter(
                new MockEnvironment()
                    .withProperty(SHOW_ACTUATOR, "true")
                    .withProperty("server.port", "8080")
                    .withProperty("management.server.port", "8081")
            )
        ).isEqualTo("false");
    }

    @Test
    void leavesTheSettingAloneWhenActuatorDocsAreOff() {
        MockEnvironment env = new MockEnvironment().withProperty("management.server.port", "8081");
        assertThat(showActuatorAfter(env)).isNull();
    }
}
