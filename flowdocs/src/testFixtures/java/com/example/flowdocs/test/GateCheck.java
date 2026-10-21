package com.example.flowdocs.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a recorded run against the declared gates: a step gated by a decision may only run after that
 * decision said yes. Catches code that calls a downstream system without asking, or despite a no.
 */
final class GateCheck {

    /** Question -> slug of the step it gates, from {@code @FlowDecision(gates = ...)}. */
    private final Map<String, String> gatedStepByQuestion;

    GateCheck(FlowGraph graph) {
        this.gatedStepByQuestion = new HashMap<>();
        graph.nodes().stream()
                .filter(n -> n.kind() == FlowGraph.Kind.DECISION)
                .forEach(n -> gatedStepByQuestion.put(n.text(), n.gates()));
    }

    /**
     * For each run of a gated step, the latest decision gating it among its ancestors' earlier children
     * must have said yes. Returns one message per violation.
     */
    List<String> violations(List<FlowRecorder.Step> roots) {
        Map<FlowRecorder.Step, FlowRecorder.Step> parents = new IdentityHashMap<>();
        roots.forEach(root -> root.flatten().forEach(s -> s.children.forEach(c -> parents.put(c, s))));

        List<String> violations = new ArrayList<>();
        roots.stream().flatMap(FlowRecorder.Step::flatten)
                .filter(s -> !s.isDecision() && gatedStepByQuestion.containsValue(FlowIds.slug(s.action)))
                .forEach(step -> {
                    FlowRecorder.Step decision = gatingDecision(step, parents);
                    if (decision == null) {
                        violations.add("\"%s\" ran without its decision being made".formatted(step.action));
                    } else if (!"yes".equals(decision.outcome())) {
                        violations.add("\"%s\" ran although \"%s\" said %s".formatted(
                                step.action, decision.action, decision.outcome()));
                    }
                });
        return violations;
    }

    /** Walks up from the step; at each ancestor, the latest earlier child decision gating it wins. */
    private FlowRecorder.Step gatingDecision(FlowRecorder.Step step, Map<FlowRecorder.Step, FlowRecorder.Step> parents) {
        String slug = FlowIds.slug(step.action);
        for (FlowRecorder.Step ancestor = parents.get(step); ancestor != null; ancestor = parents.get(ancestor)) {
            FlowRecorder.Step latest = ancestor.children.stream()
                    .filter(c -> c.isDecision() && c.startSeq < step.startSeq)
                    .filter(c -> slug.equals(gatedStepByQuestion.get(c.action)))
                    .max(Comparator.comparingLong(c -> c.startSeq))
                    .orElse(null);
            if (latest != null) {
                return latest;
            }
        }
        return null;
    }
}
