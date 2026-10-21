package com.example.router.docs;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flowdocs.test.Scenario;
import com.example.router.partner.PartnerAService;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Scenarios for the default configuration. */
@SpringBootTest(properties = {"flow.observations.enabled=true", "flow.observations.strict=true"})
class FlowScenarioDocsTest extends ScenarioDocsTestSupport {

    @Autowired
    PartnerAService partnerAService;

    static Stream<String> scenarios() throws IOException {
        return Scenario.names(RouterFlowDocs.SETTINGS.scenariosDir(), Map.of());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario(String name) throws Exception {
        run(name);
    }

    @Test
    void stepsAreProxiedWhenObservationsAreEnabled() {
        assertThat(AopUtils.isAopProxy(partnerAService)).isTrue();
    }
}
