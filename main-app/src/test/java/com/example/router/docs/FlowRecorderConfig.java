package com.example.router.docs;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class FlowRecorderConfig {

    @Bean
    FlowRecorder flowRecorder(ObservationRegistry registry) {
        FlowRecorder recorder = new FlowRecorder();
        registry.observationConfig().observationHandler(recorder.handler);
        return recorder;
    }
}
