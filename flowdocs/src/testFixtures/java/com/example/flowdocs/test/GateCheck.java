package com.example.flowdocs.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks a recorded run against the declared gates: a decision's or branch's gates step may only run after
 * it said yes, and a branch's otherwise step only after it said no. Catches code that calls a downstream
 * system without asking, or against the answer.
 */
final class GateCheck {

    /** Gated step slug -> (question -> the outcome that lets it run). */
    private final Map<String, Map<String, String>> requiredOutcomes = new HashMap<>();

    GateCheck(FlowGraph graph) {
        graph.nodes().stream().filter(FlowGraph.Node::isChoice).forEach(n -> {
            requiredOutcomes.computeIfAbsent(n.gates(), k -> new HashMap<>()).put(n.text(), "yes");
            if (n.otherwise() != null) {
                requiredOutcomes.computeIfAbsent(n.otherwise(), k -> new HashMap<>()).put(n.text(), "no");
            }
        });
    }

    /**
     * For each run of a gated step, the latest decision gating it among its ancestors' earlier children must
     * have given the outcome that lets it run. Returns one message per violation.
     */
    List<String> violations(List<FlowRecorder.Step> roots) {
        Map<FlowRecorder.Step, FlowRecorder.Step> parents = new IdentityHashMap<>();
        roots.forEach(root -> root.flatten().forEach(s -> s.children.forEach(c -> parents.put(c, s))));

        List<String> violations = new ArrayList<>();
        roots.stream().flatMap(FlowRecorder.Step::flatten)
                .filter(s -> !s.isDecision() && requiredOutcomes.containsKey(FlowIds.slug(s.action)))
                .forEach(step -> {
                    Map<String, String> required = requiredOutcomes.get(FlowIds.slug(step.action));
                    FlowRecorder.Step decision = gatingDecision(step, required, parents);
                    if (decision == null) {
                        violations.add("\"%s\" ran without its decision being made".formatted(step.action));
                    } else if (!required.get(decision.action).equals(decision.outcome())) {
                        violations.add("\"%s\" ran although \"%s\" said %s".formatted(
                                step.action, decision.action, decision.outcome()));
                    }
                });
        return violations;
    }

    /** Walks up from the step; at each ancestor, the latest earlier child decision gating it wins. */
    private static FlowRecorder.Step gatingDecision(FlowRecorder.Step step, Map<String, String> required,
                                                    Map<FlowRecorder.Step, FlowRecorder.Step> parents) {
        for (FlowRecorder.Step ancestor = parents.get(step); ancestor != null; ancestor = parents.get(ancestor)) {
            FlowRecorder.Step latest = ancestor.children.stream()
                    .filter(c -> c.isDecision() && c.startSeq < step.startSeq && required.containsKey(c.action))
                    .max(Comparator.comparingLong(c -> c.startSeq))
                    .orElse(null);
            if (latest != null) {
                return latest;
            }
        }
        return null;
    }
}
