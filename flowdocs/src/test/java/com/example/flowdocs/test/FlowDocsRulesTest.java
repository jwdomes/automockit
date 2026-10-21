package com.example.flowdocs.test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class FlowDocsRulesTest {

    @Test
    void gatedStepsReachableFromTheDecidingStepPass() {
        assertThatCode(() -> FlowDocsRules.gatedStepsAreReachable(
                new ClassFileImporter().importPackages("com.example.flowdocs.test.fixture")))
                .doesNotThrowAnyException();
    }

    @Test
    void gatedStepOnAnUnrelatedPathFails() {
        assertThatThrownBy(() -> FlowDocsRules.gatedStepsAreReachable(
                new ClassFileImporter().importPackages("com.example.flowdocs.test.badgate")))
                .hasMessageContaining("gates \"Elsewhere\", which com.example.flowdocs.test.badgate.BadGate.ask(java.lang.String) never reaches");
    }
}
