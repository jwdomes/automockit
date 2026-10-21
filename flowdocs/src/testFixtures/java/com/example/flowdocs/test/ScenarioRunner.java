package com.example.flowdocs.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * Runs one scenario against the app with its stubs loaded into WireMock, checks the status and the
 * declared gates (see {@link GateCheck}), and snapshots flows/<name>.mmd and flows/<name>.md. Create once
 * per test class; call {@link #run} from a parameterized test whose context has
 * flow.observations.enabled=true and {@link FlowRecorderConfiguration}.
 */
public class ScenarioRunner {

    private final FlowDocsSettings settings;
    private final WireMockExtension wireMock;
    private final SequenceDiagramRenderer diagrams;
    private final ScenarioMarkdown pages;
    private final GateCheck gates;

    public ScenarioRunner(FlowDocsSettings settings, WireMockExtension wireMock) {
        this.settings = settings;
        this.wireMock = wireMock;
        JavaClasses classes = settings.importClasses();
        Map<String, String> labels = FlowParticipants.labels(classes);
        this.diagrams = new SequenceDiagramRenderer(labels, settings);
        this.pages = new ScenarioMarkdown(labels);
        this.gates = new GateCheck(new FlowGraph(classes, settings.basePackage(), labels));
    }

    public void run(String name, MockMvc mvc, FlowRecorder recorder) throws Exception {
        Scenario scenario = Scenario.load(settings.scenariosDir(), name);

        wireMock.resetAll();
        recorder.clear();
        List<UUID> stubIds = new ArrayList<>();
        for (JsonNode stub : scenario.stubs()) {
            StubMapping mapping = StubMapping.buildFrom(stub.toString());
            stubIds.add(mapping.getId());
            wireMock.getRuntimeInfo().getWireMock().register(mapping);
        }

        MvcResult result = mvc.perform(request(HttpMethod.valueOf(scenario.method()), scenario.path())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scenario.request().toString()))
                .andReturn();
        MockHttpServletResponse response = result.getResponse();

        // Gates first: a skipped decision usually also changes the status, and this names the cause.
        List<FlowRecorder.Step> roots = recorder.roots();
        List<String> gateViolations = gates.violations(roots);
        assertTrue(gateViolations.isEmpty(), "Scenario " + name + " broke a declared gate: " + gateViolations);
        assertEquals(scenario.expectedStatus(), response.getStatus(), "Status for scenario " + name);
        String diagram = diagrams.render(roots, response.getStatus(),
                        scenario.method() + " " + scenario.path(), result.getResolvedException())
                .replaceFirst("\n", "\n    %% " + scenario.description() + "\n");
        Snapshots.verifyOrUpdate(settings.docsDir().resolve("flows/" + name + ".mmd"), diagram);

        List<ServeEvent> calls = new ArrayList<>(wireMock.getAllServeEvents());
        Collections.reverse(calls);   // WireMock lists newest first
        String page = pages.render(new ScenarioMarkdown.Report(name, scenario, response.getStatus(), roots, diagram,
                calls, stubIds, response.getContentAsString()));
        Snapshots.verifyOrUpdate(settings.docsDir().resolve("flows/" + name + ".md"), page);
    }
}
