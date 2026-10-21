package com.example.flowdocs.test;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The static flow graph: every step and decision, built from bytecode without starting the app. Each
 * decision is a group of condition diamonds; a step's decisions chain in call-site order, every "no"
 * leading to the next decision.
 */
public final class FlowGraph {

    public enum Kind { STEP, DECISION }

    /** {@code gates} is the gated step's id and {@code conditions} the condition labels, for decisions only. */
    public record Node(String id, String text, String participant, Kind kind, String gates, List<String> conditions,
                       JavaMethod method) {

        /** First condition diamond, or the decision's own diamond when it has no conditions. */
        String entry() {
            return conditions.isEmpty() ? id : conditionId(conditions.get(0));
        }

        String conditionId(String label) {
            return id + "__" + FlowIds.slug(label);
        }

        List<String> diamonds() {
            return conditions.isEmpty() ? List.of(id) : conditions.stream().map(this::conditionId).toList();
        }
    }

    private final String basePackage;
    private final Map<String, String> labels;
    private final Map<JavaMethod, Node> nodes = new HashMap<>();

    public FlowGraph(JavaClasses classes, String basePackage, Map<String, String> labels) {
        this.basePackage = basePackage;
        this.labels = labels;
        for (JavaClass c : classes) {
            for (JavaMethod m : c.getMethods()) {
                m.tryGetAnnotationOfType(FlowStep.class).ifPresent(fs -> nodes.put(m, new Node(
                        FlowIds.slug(fs.action()), fs.action(), fs.participant(), Kind.STEP, null, List.of(), m)));
                m.tryGetAnnotationOfType(FlowDecision.class).ifPresent(fd -> nodes.put(m, new Node(
                        FlowIds.slug(fd.question()), fd.question(), fd.participant(), Kind.DECISION,
                        FlowIds.slug(fd.gates()), Arrays.stream(fd.conditions()).map(Condition::label).toList(), m)));
            }
        }
    }

    public List<Node> nodes() {
        return nodes.values().stream()
                .sorted(Comparator.comparing(Node::participant).thenComparing(Node::text))
                .toList();
    }

    public String mermaid() {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Map<String, List<Node>> byParticipant = nodes.values().stream()
                .collect(Collectors.groupingBy(Node::participant, TreeMap::new, Collectors.toList()));
        byParticipant.forEach((participant, members) -> {
            sb.append("    subgraph %s[\"%s\"]\n".formatted(participant, FlowIds.escape(label(participant))));
            members.stream().sorted(Comparator.comparing(Node::id)).forEach(n -> renderNode(n, sb));
            sb.append("    end\n");
        });

        Set<String> edges = new TreeSet<>();
        Set<String> gated = nodes.values().stream()
                .filter(n -> n.kind() == Kind.DECISION).map(Node::gates).collect(Collectors.toSet());
        nodes.forEach((m, from) -> {
            if (from.kind() != Kind.STEP) {
                return;
            }
            // Gated steps get their incoming edge from their decision instead.
            reachable(m).stream()
                    .filter(to -> to.kind() == Kind.STEP && !gated.contains(to.id()))
                    .forEach(to -> edges.add("    %s --> %s".formatted(from.id(), to.id())));
            chainDecisions(from, edges);
        });
        errorHandlerEdges(edges);
        edges.forEach(e -> sb.append(e).append('\n'));
        return sb.toString();
    }

    private String label(String participant) {
        String label = labels.get(participant);
        if (label == null) {
            throw new IllegalStateException("No @FlowParticipant declares id " + participant);
        }
        return label;
    }

    private static void renderNode(Node n, StringBuilder sb) {
        if (n.kind() == Kind.STEP) {
            sb.append("        %s[\"%s\"]\n".formatted(n.id(), FlowIds.escape(n.text())));
        } else if (n.conditions().isEmpty()) {
            sb.append("        %s{\"%s\"}\n".formatted(n.id(), FlowIds.escape(n.text())));
        } else {
            sb.append("        subgraph %s[\"%s\"]\n".formatted(n.id(), FlowIds.escape(n.text())));
            n.conditions().forEach(c -> sb.append("            %s{\"%s?\"}\n".formatted(n.conditionId(c), FlowIds.escape(c))));
            sb.append("        end\n");
        }
    }

    /**
     * Decisions a step calls directly, in call-site order. Within a decision each condition's yes leads to
     * the next condition and the last one's to the gated step; every no, and the gated step, lead on to
     * the next decision.
     */
    private void chainDecisions(Node from, Set<String> edges) {
        List<Node> decisions = from.method().getMethodCallsFromSelf().stream()
                .sorted(Comparator.comparingInt(JavaMethodCall::getLineNumber))
                .flatMap(call -> resolve(call).stream())
                .map(nodes::get)
                .filter(n -> n != null && n.kind() == Kind.DECISION)
                .distinct()
                .toList();
        for (int i = 0; i < decisions.size(); i++) {
            Node decision = decisions.get(i);
            String next = i + 1 < decisions.size() ? decisions.get(i + 1).entry() : null;
            if (i == 0) {
                edges.add("    %s --> %s".formatted(from.id(), decision.entry()));
            }
            List<String> diamonds = decision.diamonds();
            for (int j = 0; j < diamonds.size(); j++) {
                String yes = j + 1 < diamonds.size() ? diamonds.get(j + 1) : decision.gates();
                edges.add("    %s -->|yes| %s".formatted(diamonds.get(j), yes));
                if (next != null) {
                    edges.add("    %s -->|no| %s".formatted(diamonds.get(j), next));
                }
            }
            if (next != null) {
                edges.add("    %s --> %s".formatted(decision.gates(), next));
            }
        }
    }

    /** Handler steps aren't called from code, so link each controller step to them, labelled by exception. */
    private void errorHandlerEdges(Set<String> edges) {
        List<Node> controllers = stepsInClassesAnnotatedWith(RestController.class);
        for (Node handler : stepsInClassesAnnotatedWith(RestControllerAdvice.class)) {
            String exceptions = handler.method().tryGetAnnotationOfType(ExceptionHandler.class)
                    .map(eh -> Arrays.stream(eh.value()).map(Class::getSimpleName).collect(Collectors.joining(", ")))
                    .orElse("");
            controllers.forEach(c -> edges.add(
                    "    %s -.->|\"%s\"| %s".formatted(c.id(), FlowIds.escape(exceptions), handler.id())));
        }
    }

    private List<Node> stepsInClassesAnnotatedWith(Class<? extends Annotation> annotation) {
        return nodes.values().stream()
                .filter(n -> n.kind() == Kind.STEP && n.method().getOwner().isAnnotatedWith(annotation))
                .toList();
    }

    /** Whether {@code from} reaches the step {@code stepId}, directly or through unannotated helpers. */
    boolean reaches(JavaMethod from, String stepId) {
        return reachable(from).stream().anyMatch(n -> n.kind() == Kind.STEP && n.id().equals(stepId));
    }

    /** Follow calls through unannotated helpers until reaching other steps or decisions. */
    private Set<Node> reachable(JavaMethod start) {
        Set<Node> found = new HashSet<>();
        Set<JavaMethod> visited = new HashSet<>(Set.of(start));
        Deque<JavaMethod> stack = new ArrayDeque<>(List.of(start));
        while (!stack.isEmpty()) {
            for (JavaMethodCall call : stack.pop().getMethodCallsFromSelf()) {
                for (JavaMethod target : resolve(call)) {
                    if (target.equals(start)) {
                        continue;
                    }
                    Node node = nodes.get(target);
                    if (node != null) {
                        found.add(node);                 // stop: that node owns its edges
                    } else if (visited.add(target)) {
                        stack.push(target);              // traverse helper
                    }
                }
            }
        }
        return found;
    }

    private List<JavaMethod> resolve(JavaMethodCall call) {
        return call.getTarget().resolveMember().stream()
                .filter(t -> t.getOwner().getPackageName().startsWith(basePackage))
                .flatMap(this::withImplementations)
                .toList();
    }

    /** Calls through an injected interface resolve to the interface; fan out to implementations. */
    private Stream<JavaMethod> withImplementations(JavaMethod target) {
        if (!target.getOwner().isInterface()) {
            return Stream.of(target);
        }
        String[] params = target.getRawParameterTypes().stream().map(JavaClass::getName).toArray(String[]::new);
        return Stream.concat(Stream.of(target), target.getOwner().getAllSubclasses().stream()
                .flatMap(impl -> impl.tryGetMethod(target.getName(), params).stream()));
    }
}
