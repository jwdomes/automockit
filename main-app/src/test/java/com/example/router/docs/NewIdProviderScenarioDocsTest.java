package com.example.router.docs;

import com.example.flowdocs.test.Scenario;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

/** Scenarios for id-provider.use-new=true. */
@SpringBootTest(properties = {
        "flow.observations.enabled=true", "flow.observations.strict=true", "id-provider.use-new=true"})
class NewIdProviderScenarioDocsTest extends ScenarioDocsTestSupport {

    static Stream<String> scenarios() throws IOException {
        return Scenario.names(RouterFlowDocs.SETTINGS.scenariosDir(), Map.of("id-provider.use-new", "true"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name) throws Exception {
        run(name);
    }
}
