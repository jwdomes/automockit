package com.example.flowdocs.test;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Import into a @SpringBootTest that runs scenarios with flow.observations.enabled=true. */
@TestConfiguration(proxyBeanMethods = false)
public class FlowRecorderConfiguration {

    @Bean
    FlowRecorder flowRecorder(ObservationRegistry registry) {
        FlowRecorder recorder = new FlowRecorder();
        registry.observationConfig().observationHandler(recorder.handler);
        return recorder;
    }
}
