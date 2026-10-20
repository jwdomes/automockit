package com.example.router.docs;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
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

class FlowDocsTest {

    private static final String BASE_PKG = "com.example.router";

    record Node(String id, String action, Participant participant, String when) {
    }

    private final Map<JavaMethod, Node> nodes = new HashMap<>();

    @Test
    void flowDiagramIsCurrent() throws Exception {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PKG);
        for (JavaClass c : classes) {
            for (JavaMethod m : c.getMethods()) {
                m.tryGetAnnotationOfType(FlowStep.class).ifPresent(fs -> nodes.put(m,
                        new Node(slug(fs.action()), fs.action(), fs.participant(), fs.when())));
            }
        }
        assertFalse(nodes.isEmpty(), "No @FlowStep methods found under " + BASE_PKG);
        Snapshots.verifyOrUpdate(Path.of("docs/flow.mmd"), render());
    }

    /** "Call Partner A" -> "call_partner_a". Also used by the uniqueness rule. */
    static String slug(String action) {
        return action.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    /** Follow calls through unannotated helpers until reaching other steps. */
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
                        found.add(node);                 // stop: that step owns its edges
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

    private String render() {
        StringBuilder sb = new StringBuilder("flowchart TD\n");

        Map<Participant, List<Node>> byParticipant = nodes.values().stream().collect(Collectors.groupingBy(
                Node::participant, () -> new EnumMap<>(Participant.class), Collectors.toList()));
        byParticipant.forEach((p, members) -> {
            sb.append("    subgraph %s[\"%s\"]\n".formatted(p.name(), escape(p.label())));
            members.stream().sorted(Comparator.comparing(Node::id))
                    .forEach(n -> sb.append("        %s[\"%s\"]\n".formatted(n.id(), escape(n.action()))));
            sb.append("    end\n");
        });

        Set<String> edges = new TreeSet<>();
        nodes.forEach((m, from) -> reachable(m).forEach(to -> edges.add(to.when().isBlank()
                ? "    %s --> %s".formatted(from.id(), to.id())
                : "    %s -->|\"%s\"| %s".formatted(from.id(), escape(to.when()), to.id()))));
        edges.forEach(e -> sb.append(e).append('\n'));
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\"", "#quot;");
    }
}
