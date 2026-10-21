package com.example.router.docs;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.example.flowdocs.test.FlowRecorder;
import com.example.flowdocs.test.FlowRecorderConfiguration;
import com.example.flowdocs.test.ScenarioRunner;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared wiring for the scenario tests. Each subclass sets its context's properties in @SpringBootTest and
 * runs the scenarios declaring exactly those properties.
 */
@AutoConfigureMockMvc
@Import(FlowRecorderConfiguration.class)
abstract class ScenarioDocsTestSupport {

    // Plain-text HTTP/2 off: WireMock's Jetty resets the JDK client's h2c upgrade attempt.
    @RegisterExtension
    static WireMockExtension downstream = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    private static final ScenarioRunner RUNNER = new ScenarioRunner(RouterFlowDocs.SETTINGS, downstream);

    @DynamicPropertySource
    static void downstreamUrls(DynamicPropertyRegistry registry) {
        registry.add("partners.a.base-url", () -> downstream.baseUrl() + "/partner-a");
        registry.add("partners.b.base-url", () -> downstream.baseUrl() + "/partner-b");
        registry.add("partners.c.base-url", () -> downstream.baseUrl() + "/partner-c");
        registry.add("id-provider.new.base-url", () -> downstream.baseUrl() + "/new-id-provider");
        registry.add("id-provider.legacy.base-url", () -> downstream.baseUrl() + "/legacy-id-provider");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FlowRecorder recorder;

    void run(String scenario) throws Exception {
        RUNNER.run(scenario, mvc, recorder);
    }
}
