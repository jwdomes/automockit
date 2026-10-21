package com.example.flowdocs.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.ObservationRegistry;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlowDocsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlowDocsAutoConfiguration.class))
            .withBean(ObservationRegistry.class, ObservationRegistry::create);

    /** The production guarantee: with the property unset, the library contributes nothing to the context. */
    @Test
    void registersNothingByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(Advisor.class);
            assertThat(context).doesNotHaveBean(FlowDocsAutoConfiguration.class);
            assertThat(Arrays.stream(context.getBeanDefinitionNames()).filter(n -> n.toLowerCase().contains("flow")))
                    .isEmpty();
        });
    }

    @Test
    void registersNothingWhenExplicitlyDisabled() {
        runner.withPropertyValues("flow.observations.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Advisor.class));
    }

    @Test
    void registersTheAdvisorWhenEnabled() {
        runner.withPropertyValues("flow.observations.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(Advisor.class));
    }
}
