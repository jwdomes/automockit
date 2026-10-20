package com.example.router.docs;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.router.client.PartnerAClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "flow.observations.enabled=true")
@AutoConfigureMockMvc
@Import(FlowRecorderConfig.class)
class FlowScenarioDocsTest {

    private static final Path SCENARIOS = Path.of("src/test/resources/flow-scenarios");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SequenceDiagramRenderer RENDERER = new SequenceDiagramRenderer();
    private static final ScenarioMarkdown MARKDOWN = new ScenarioMarkdown();

    record Scenario(String description, JsonNode request, int expectedStatus, List<JsonNode> stubs) {
    }

    @RegisterExtension
    static WireMockExtension partners = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void partnerUrls(DynamicPropertyRegistry registry) {
        registry.add("partners.a.base-url", () -> partners.baseUrl() + "/partner-a");
        registry.add("partners.b.base-url", () -> partners.baseUrl() + "/partner-b");
        registry.add("partners.c.base-url", () -> partners.baseUrl() + "/partner-c");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    FlowRecorder recorder;

    @Autowired
    PartnerAClient partnerAClient;

    static Stream<String> scenarios() throws IOException {
        try (Stream<Path> files = Files.list(SCENARIOS)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - ".json".length()))
                    .sorted().toList().stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name) throws Exception {
        Scenario s = JSON.readValue(Files.readString(SCENARIOS.resolve(name + ".json")), Scenario.class);

        partners.resetAll();
        recorder.clear();
        List<UUID> stubIds = new ArrayList<>();
        for (JsonNode stub : s.stubs()) {
            StubMapping mapping = StubMapping.buildFrom(stub.toString());
            stubIds.add(mapping.getId());
            partners.getRuntimeInfo().getWireMock().register(mapping);
        }

        MockHttpServletResponse response = mvc.perform(
                        post("/search").contentType(MediaType.APPLICATION_JSON).content(s.request().toString()))
                .andExpect(status().is(s.expectedStatus()))
                .andReturn().getResponse();

        String diagram = RENDERER.render(recorder.roots(), response.getStatus())
                .replaceFirst("\n", "\n    %% " + s.description() + "\n");
        Snapshots.verifyOrUpdate(Path.of("docs/flows/" + name + ".mmd"), diagram);

        List<ServeEvent> partnerCalls = new ArrayList<>(partners.getAllServeEvents());
        Collections.reverse(partnerCalls);   // WireMock lists newest first
        ScenarioReport report = new ScenarioReport(name, s.description(), s.expectedStatus(), response.getStatus(),
                s.request(), recorder.roots(), diagram, partnerCalls, stubIds, response.getContentAsString());
        Snapshots.verifyOrUpdate(Path.of("docs/flows/" + name + ".md"), MARKDOWN.render(report));
    }

    @Test
    void stepsAreProxiedWhenObservationsAreEnabled() {
        assertThat(AopUtils.isAopProxy(partnerAClient)).isTrue();
    }
}
