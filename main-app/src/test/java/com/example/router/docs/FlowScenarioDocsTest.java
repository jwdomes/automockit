package com.example.router.docs;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.flowdocs.test.FlowRecorder;
import com.example.flowdocs.test.FlowRecorderConfiguration;
import com.example.flowdocs.test.Scenario;
import com.example.flowdocs.test.ScenarioRunner;
import com.example.router.partner.PartnerAService;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"flow.observations.enabled=true", "flow.observations.strict=true"})
@AutoConfigureMockMvc
@Import(FlowRecorderConfiguration.class)
class FlowScenarioDocsTest {

    // Plain-text HTTP/2 off: WireMock's Jetty resets the JDK client's h2c upgrade attempt.
    @RegisterExtension
    static WireMockExtension partners = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    private static final ScenarioRunner RUNNER = new ScenarioRunner(RouterFlowDocs.SETTINGS, partners);

    @DynamicPropertySource
    static void partnerUrls(DynamicPropertyRegistry registry) {
        registry.add("partners.a.base-url", () -> partners.baseUrl() + "/partner-a");
        registry.add("partners.b.base-url", () -> partners.baseUrl() + "/partner-b");
        registry.add("partners.c.base-url", () -> partners.baseUrl() + "/partner-c");
        registry.add("id-provider.new.base-url", () -> partners.baseUrl() + "/new-id-provider");
        registry.add("id-provider.legacy.base-url", () -> partners.baseUrl() + "/legacy-id-provider");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FlowRecorder recorder;

    @Autowired
    PartnerAService partnerAService;

    static Stream<String> scenarios() throws IOException {
        return Scenario.names(RouterFlowDocs.SETTINGS.scenariosDir());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name) throws Exception {
        RUNNER.run(name, mvc, recorder);
    }

    @Test
    void stepsAreProxiedWhenObservationsAreEnabled() {
        assertThat(AopUtils.isAopProxy(partnerAService)).isTrue();
    }
}
