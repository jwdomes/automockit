package com.example.flowdocs.test;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowBranch;
import com.example.flowdocs.FlowBranches;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * The static flow graph: every step, decision and branch, built from bytecode without starting the app.
 * Each decision or branch is a group of condition diamonds; a step's decisions and branches chain in
 * code order, every "no" leading to the branch's otherwise step or else the next decision.
 */
public final class FlowGraph {

    public enum Kind { STEP, DECISION, BRANCH }

    /**
     * For decisions and branches: {@code gates} is the yes step's id, {@code otherwise} the no step's id (branches
     * only; null when there is none) and {@code conditions} the condition labels. A branch's {@code method} is the
     * step method that declares it.
     */
    public record Node(String id, String text, String participant, Kind kind, String gates, String otherwise,
                       List<String> conditions, JavaMethod method) {

        boolean isChoice() {
            return kind != Kind.STEP;
        }

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
    /** Steps and decisions, by the method they annotate. */
    private final Map<JavaMethod, Node> nodes = new HashMap<>();
    private final List<Node> branches = new ArrayList<>();

    public FlowGraph(JavaClasses classes, String basePackage, Map<String, String> labels) {
        this.basePackage = basePackage;
        this.labels = labels;
        for (JavaClass c : classes) {
            for (JavaMethod m : c.getMethods()) {
                m.tryGetAnnotationOfType(FlowStep.class).ifPresent(fs -> nodes.put(m, new Node(
                        FlowIds.slug(fs.action()), fs.action(), fs.participant(), Kind.STEP, null, null, List.of(), m)));
                m.tryGetAnnotationOfType(FlowDecision.class).ifPresent(fd -> nodes.put(m, new Node(
                        FlowIds.slug(fd.question()), fd.question(), fd.participant(), Kind.DECISION,
                        FlowIds.slug(fd.gates()), null, labels(fd.conditions()), m)));
                for (FlowBranch fb : branchesOf(m)) {
                    branches.add(new Node(FlowIds.slug(fb.question()), fb.question(), fb.participant(), Kind.BRANCH,
                            FlowIds.slug(fb.gates()), fb.otherwise().isEmpty() ? null : FlowIds.slug(fb.otherwise()),
                            labels(fb.conditions()), m));
                }
            }
        }
    }

    /** @FlowBranch annotations on a method, whether single or repeated (in their container). */
    static List<FlowBranch> branchesOf(JavaMethod method) {
        List<FlowBranch> found = new ArrayList<>();
        method.tryGetAnnotationOfType(FlowBranch.class).ifPresent(found::add);
        method.tryGetAnnotationOfType(FlowBranches.class).ifPresent(container -> found.addAll(List.of(container.value())));
        return found;
    }

    private static List<String> labels(Condition[] conditions) {
        return Arrays.stream(conditions).map(Condition::label).toList();
    }

    public List<Node> nodes() {
        return Stream.concat(nodes.values().stream(), branches.stream())
                .sorted(Comparator.comparing(Node::participant).thenComparing(Node::text))
                .toList();
    }

    public String mermaid() {
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        Map<String, List<Node>> byParticipant = nodes().stream()
                .collect(Collectors.groupingBy(Node::participant, TreeMap::new, Collectors.toList()));
        byParticipant.forEach((participant, members) -> {
            sb.append("    subgraph %s[\"%s\"]\n".formatted(participant, FlowIds.escape(label(participant))));
            members.stream().sorted(Comparator.comparing(Node::id)).forEach(n -> renderNode(n, sb));
            sb.append("    end\n");
        });

        Set<String> edges = new TreeSet<>();
        Set<String> gated = nodes().stream().filter(Node::isChoice)
                .flatMap(n -> Stream.of(n.gates(), n.otherwise())).filter(id -> id != null)
                .collect(Collectors.toSet());
        nodes.forEach((m, from) -> {
            if (from.kind() != Kind.STEP) {
                return;
            }
            // Gated steps get their incoming edge from their decision or branch instead.
            reachable(m).stream()
                    .filter(to -> to.kind() == Kind.STEP && !gated.contains(to.id()))
                    .forEach(to -> edges.add("    %s --> %s".formatted(from.id(), to.id())));
            chainChoices(from, edges);
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
     * A step's decisions and branches, in code order: a decision at the line that calls it, a branch at the
     * first line that reaches its gates or otherwise step. Within each, a condition's yes leads to the next
     * condition and the last one's to the gated step; every no leads to the branch's otherwise step if it has
     * one, else to the next entry; the gated and otherwise steps lead on to the next entry.
     */
    private void chainChoices(Node from, Set<String> edges) {
        Map<Node, Integer> lines = new LinkedHashMap<>();
        from.method().getMethodCallsFromSelf().stream()
                .sorted(Comparator.comparingInt(JavaMethodCall::getLineNumber))
                .forEach(call -> resolve(call).stream()
                        .map(nodes::get)
                        .filter(n -> n != null && n.kind() == Kind.DECISION)
                        .forEach(n -> lines.putIfAbsent(n, call.getLineNumber())));
        branches.stream()
                .filter(b -> b.method().equals(from.method()))
                .forEach(b -> lines.put(b, firstLineReaching(from.method(), b)));
        List<Node> choices = lines.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        for (int i = 0; i < choices.size(); i++) {
            Node choice = choices.get(i);
            String next = i + 1 < choices.size() ? choices.get(i + 1).entry() : null;
            if (i == 0) {
                edges.add("    %s --> %s".formatted(from.id(), choice.entry()));
            }
            String no = choice.otherwise() != null ? choice.otherwise() : next;
            List<String> diamonds = choice.diamonds();
            for (int j = 0; j < diamonds.size(); j++) {
                String yes = j + 1 < diamonds.size() ? diamonds.get(j + 1) : choice.gates();
                edges.add("    %s -->|yes| %s".formatted(diamonds.get(j), yes));
                if (no != null) {
                    edges.add("    %s -->|no| %s".formatted(diamonds.get(j), no));
                }
            }
            if (next != null) {
                edges.add("    %s --> %s".formatted(choice.gates(), next));
                if (choice.otherwise() != null) {
                    edges.add("    %s --> %s".formatted(choice.otherwise(), next));
                }
            }
        }
    }

    /** Line of the first call in {@code method} that reaches the branch's gates or otherwise step. */
    private int firstLineReaching(JavaMethod method, Node branch) {
        Set<String> targets = new HashSet<>();
        targets.add(branch.gates());
        if (branch.otherwise() != null) {
            targets.add(branch.otherwise());
        }
        return method.getMethodCallsFromSelf().stream()
                .sorted(Comparator.comparingInt(JavaMethodCall::getLineNumber))
                .filter(call -> resolve(call).stream().anyMatch(target -> {
                    Node node = nodes.get(target);
                    return node != null
                            ? targets.contains(node.id())
                            : reachable(target).stream().anyMatch(n -> targets.contains(n.id()));
                }))
                .mapToInt(JavaMethodCall::getLineNumber)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
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
