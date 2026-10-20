package com.example.router.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Renders docs/flow.mmd (every step, decision and path) and the docs/README.md index. */
class FlowDocsTest {

    private static final String BASE_PKG = "com.example.router";
    private static final Path SCENARIOS = Path.of("src/test/resources/flow-scenarios");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    enum Kind { STEP, DECISION }

    /** {@code gates} is the gated step's id, for decisions only. */
    record Node(String id, String text, Participant participant, Kind kind, String gates, JavaMethod method) {
    }

    private final Map<JavaMethod, Node> nodes = new HashMap<>();

    @Test
    void flowDocsAreCurrent() throws Exception {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PKG);
        for (JavaClass c : classes) {
            for (JavaMethod m : c.getMethods()) {
                m.tryGetAnnotationOfType(FlowStep.class).ifPresent(fs -> nodes.put(m,
                        new Node(slug(fs.action()), fs.action(), fs.participant(), Kind.STEP, null, m)));
                m.tryGetAnnotationOfType(FlowDecision.class).ifPresent(fd -> nodes.put(m,
                        new Node(slug(fd.question()), fd.question(), fd.participant(), Kind.DECISION,
                                slug(fd.gates()), m)));
            }
        }
        assertFalse(nodes.isEmpty(), "No @FlowStep methods found under " + BASE_PKG);
        String graph = renderGraph();
        Snapshots.verifyOrUpdate(Path.of("docs/flow.mmd"), graph);
        Snapshots.verifyOrUpdate(Path.of("docs/README.md"), renderIndex(graph));
    }

    /** "Call Partner A" -> "call_partner_a". Also used by the uniqueness rule. */
    static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private String renderGraph() {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        Map<Participant, List<Node>> byParticipant = nodes.values().stream().collect(Collectors.groupingBy(
                Node::participant, () -> new EnumMap<>(Participant.class), Collectors.toList()));
        byParticipant.forEach((p, members) -> {
            sb.append("    subgraph %s[\"%s\"]\n".formatted(p.name(), escape(p.label())));
            members.stream().sorted(Comparator.comparing(Node::id)).forEach(n -> sb.append(n.kind() == Kind.DECISION
                    ? "        %s{\"%s\"}\n".formatted(n.id(), escape(n.text()))
                    : "        %s[\"%s\"]\n".formatted(n.id(), escape(n.text()))));
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

    /**
     * Decisions a step calls directly, in call-site order, form a chain: yes runs the gated step, and both
     * the gated step and no lead on to the next decision.
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
            if (i == 0) {
                edges.add("    %s --> %s".formatted(from.id(), decision.id()));
            }
            edges.add("    %s -->|yes| %s".formatted(decision.id(), decision.gates()));
            if (i + 1 < decisions.size()) {
                String next = decisions.get(i + 1).id();
                edges.add("    %s -->|no| %s".formatted(decision.id(), next));
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
                    "    %s -.->|\"%s\"| %s".formatted(c.id(), escape(exceptions), handler.id())));
        }
    }

    private List<Node> stepsInClassesAnnotatedWith(Class<? extends Annotation> annotation) {
        return nodes.values().stream()
                .filter(n -> n.kind() == Kind.STEP && n.method().getOwner().isAnnotatedWith(annotation))
                .toList();
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
                .filter(t -> t.getOwner().getPackageName().startsWith(BASE_PKG))
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

    private String renderIndex(String graph) throws IOException {
        StringBuilder sb = new StringBuilder("""
                # Flow docs

                Generated by `FlowDocsTest` and `FlowScenarioDocsTest`. Don't edit by hand: run
                `./gradlew :main-app:test -PupdateFlowDocs` and commit the result.

                ## Flow

                ```mermaid
                """).append(graph).append("""
                ```

                ## Steps and decisions

                | Name | Kind | Participant | Code |
                | --- | --- | --- | --- |
                """);
        nodes.values().stream()
                .sorted(Comparator.comparing(Node::participant).thenComparing(Node::text))
                .forEach(n -> sb.append("| %s | %s | %s | %s |\n".formatted(
                        n.text(), n.kind().name().toLowerCase(Locale.ROOT), n.participant().label(), codeLink(n))));
        sb.append("""

                ## Scenarios

                | Scenario | Description | Expected status |
                | --- | --- | --- |
                """);
        try (Stream<Path> files = Files.list(SCENARIOS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonNode scenario = JSON.readTree(file.toFile());
                sb.append("| [%s](flows/%s.md) | %s | %d |\n".formatted(name, name,
                        scenario.get("description").asString(), scenario.get("expectedStatus").asInt()));
            }
        }
        return sb.toString();
    }

    /** Class#method linking to the source file; no line numbers, so unrelated edits don't stale the doc. */
    private static String codeLink(Node n) {
        JavaClass owner = n.method().getOwner();
        String file = "../src/main/java/%s/%s".formatted(
                owner.getPackageName().replace('.', '/'), owner.getSourceCodeLocation().getSourceFileName());
        return "[`%s#%s`](%s)".formatted(owner.getSimpleName(), n.method().getName(), file);
    }

    private static String escape(String s) {
        return s.replace("\"", "#quot;");
    }
}
