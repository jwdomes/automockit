package com.example.flowdocs.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flowdocs.boot.FlowObservationKeys;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fixture gates: "First?" gates "Call one", "Second?" gates "Call two"; branch "Fast?" gates "Call fast", otherwise "Call slow". */
class GateCheckTest {

    private static final String FIXTURE = "com.example.flowdocs.test.fixture";

    private final GateCheck check;

    GateCheckTest() {
        JavaClasses classes = new ClassFileImporter().importPackages(FIXTURE);
        check = new GateCheck(new FlowGraph(classes, FIXTURE, FlowParticipants.labels(classes)));
    }

    @Test
    void gatedStepAfterAYesPasses() {
        assertThat(check.violations(List.of(parent(decision("First?", "yes", 2), step("Call one", 3))))).isEmpty();
    }

    @Test
    void gatedStepAfterANoFails() {
        assertThat(check.violations(List.of(parent(decision("First?", "no", 2), step("Call one", 3)))))
                .containsExactly("\"Call one\" ran although \"First?\" said no");
    }

    @Test
    void gatedStepWithoutItsDecisionFails() {
        assertThat(check.violations(List.of(parent(decision("Second?", "yes", 2), step("Call one", 3)))))
                .containsExactly("\"Call one\" ran without its decision being made");
    }

    @Test
    void theLatestDecisionBeforeTheStepCounts() {
        assertThat(check.violations(List.of(parent(
                decision("First?", "no", 2), decision("First?", "yes", 3), step("Call one", 4))))).isEmpty();
    }

    @Test
    void ungatedStepsAreIgnored() {
        assertThat(check.violations(List.of(parent(step("Receive", 2))))).isEmpty();
    }

    @Test
    void otherwiseStepAfterANoPasses() {
        assertThat(check.violations(List.of(parent(decision("Fast?", "no", 2), step("Call slow", 3))))).isEmpty();
    }

    @Test
    void otherwiseStepAfterAYesFails() {
        assertThat(check.violations(List.of(parent(decision("Fast?", "yes", 2), step("Call slow", 3)))))
                .containsExactly("\"Call slow\" ran although \"Fast?\" said yes");
    }

    private static FlowRecorder.Step parent(FlowRecorder.Step... children) {
        FlowRecorder.Step parent = step("Orchestrate", 1);
        parent.children.addAll(List.of(children));
        return parent;
    }

    private static FlowRecorder.Step step(String action, long seq) {
        return new FlowRecorder.Step(new Observation.Context(), action, "APP", FlowObservationKeys.KIND_STEP, seq);
    }

    private static FlowRecorder.Step decision(String question, String outcome, long seq) {
        Observation.Context context = new Observation.Context();
        context.addLowCardinalityKeyValue(KeyValue.of(FlowObservationKeys.OUTCOME_KEY, outcome));
        return new FlowRecorder.Step(context, question, "APP", FlowObservationKeys.KIND_DECISION, seq);
    }
}
