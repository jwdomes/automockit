package com.example.flowdocs.test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import com.example.flowdocs.boot.FlowExpressions;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.expression.ParseException;

/**
 * Rules that keep the annotations honest. Include them with
 * {@code @ArchTest static final ArchTests flowDocs = ArchTests.in(FlowDocsRules.class);} in a class annotated
 * {@code @AnalyzeClasses(packages = "<base package>", importOptions = ImportOption.DoNotIncludeTests.class)}.
 */
public final class FlowDocsRules {

    private static final Set<String> HTTP_CLIENTS = Set.of(
            "org.springframework.web.client.RestClient",
            "org.springframework.web.client.RestTemplate",
            "org.springframework.web.reactive.function.client.WebClient");

    /** A downstream call that isn't a step would be missing from every diagram. */
    @ArchTest
    public static final ArchRule httpCallsAreSteps =
            methods().that(callAnHttpClient()).should().beAnnotatedWith(FlowStep.class);

    @ArchTest
    public static final ArchRule flowMethodsAreProxyable =
            methods().that().areAnnotatedWith(FlowStep.class).or().areAnnotatedWith(FlowDecision.class)
                    .should().notHaveModifier(JavaModifier.PRIVATE)
                    .andShould().notHaveModifier(JavaModifier.FINAL)
                    .andShould().notHaveModifier(JavaModifier.STATIC);

    @ArchTest
    public static final ArchRule noSelfInvocation =
            methods().that().areAnnotatedWith(FlowStep.class).or().areAnnotatedWith(FlowDecision.class)
                    .should(notBeCalledFromOwnClass());

    @ArchTest
    public static final ArchRule decisionsReturnBoolean =
            methods().that().areAnnotatedWith(FlowDecision.class)
                    .should().haveRawReturnType(boolean.class).orShould().haveRawReturnType(Boolean.class);

    /** The static graph orders decisions by call site within a step, so they must be called from one. */
    @ArchTest
    public static final ArchRule decisionsAreCalledFromSteps =
            methods().that().areAnnotatedWith(FlowDecision.class).should(onlyBeCalledFromSteps());

    /** Steps and decisions share one id namespace in the static graph. */
    @ArchTest
    public static void namesAreUnique(JavaClasses classes) {
        Map<String, List<String>> bySlug = new TreeMap<>();
        flowMethods(classes).forEach(m -> bySlug
                .computeIfAbsent(FlowIds.slug(flowName(m)), k -> new ArrayList<>())
                .add(m.getFullName()));
        bySlug.values().removeIf(methods -> methods.size() < 2);
        assertTrue(bySlug.isEmpty(), "Duplicate @FlowStep actions or @FlowDecision questions: " + bySlug);
    }

    @ArchTest
    public static void decisionTargetsExist(JavaClasses classes) {
        Set<String> actions = classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(FlowStep.class))
                .map(m -> m.getAnnotationOfType(FlowStep.class).action())
                .collect(Collectors.toSet());
        List<String> missing = classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(FlowDecision.class))
                .filter(m -> !actions.contains(m.getAnnotationOfType(FlowDecision.class).gates()))
                .map(m -> m.getFullName() + " gates \"" + m.getAnnotationOfType(FlowDecision.class).gates() + "\"")
                .toList();
        assertTrue(missing.isEmpty(), "@FlowDecision gates a step that doesn't exist: " + missing);
    }

    /**
     * The step that asks a decision must also be able to reach the step it gates. Catches a gates value
     * pointing at a step on an unrelated path; GateCheck covers what actually runs.
     */
    @ArchTest
    public static void gatedStepsAreReachable(JavaClasses classes) {
        FlowGraph graph = new FlowGraph(classes, "", Map.of());
        List<String> unreachable = new ArrayList<>();
        classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(FlowDecision.class))
                .forEach(decision -> {
                    String gates = decision.getAnnotationOfType(FlowDecision.class).gates();
                    decision.getCallsOfSelf().stream()
                            .map(call -> call.getOrigin())
                            .filter(origin -> origin instanceof JavaMethod && origin.isAnnotatedWith(FlowStep.class))
                            .map(JavaMethod.class::cast)
                            .filter(caller -> !graph.reaches(caller, FlowIds.slug(gates)))
                            .forEach(caller -> unreachable.add("%s gates \"%s\", which %s never reaches".formatted(
                                    decision.getFullName(), gates, caller.getFullName())));
                });
        assertTrue(unreachable.isEmpty(), "Gated steps not reachable from the deciding step: " + unreachable);
    }

    @ArchTest
    public static void participantsAreDeclared(JavaClasses classes) {
        Map<String, List<String>> declared = new TreeMap<>();
        FlowParticipants.declarations(classes).forEach(d ->
                declared.computeIfAbsent(d.id(), k -> new ArrayList<>()).add(d.field()));
        List<String> duplicates = declared.entrySet().stream()
                .filter(e -> e.getValue().size() > 1).map(e -> e.getKey() + " " + e.getValue()).toList();
        List<String> undeclared = flowMethods(classes)
                .filter(m -> !declared.containsKey(participant(m)))
                .map(m -> m.getFullName() + " uses \"" + participant(m) + "\"")
                .toList();
        assertTrue(duplicates.isEmpty(), "Participant ids declared more than once: " + duplicates);
        assertTrue(undeclared.isEmpty(), "Participants with no @FlowParticipant constant: " + undeclared);
    }

    /** Catches expression typos on paths no scenario exercises. */
    @ArchTest
    public static void expressionsParse(JavaClasses classes) {
        List<String> failures = new ArrayList<>();
        flowMethods(classes).forEach(m -> {
            m.tryGetAnnotationOfType(FlowStep.class).filter(s -> !s.note().isEmpty())
                    .ifPresent(s -> parse(() -> FlowExpressions.parseNote(s.note()), m, "note", failures));
            m.tryGetAnnotationOfType(FlowDecision.class).ifPresent(d -> {
                for (Condition c : d.conditions()) {
                    parse(() -> FlowExpressions.parseCondition(c.test()), m, "condition \"" + c.label() + "\"", failures);
                }
            });
        });
        assertTrue(failures.isEmpty(), "Unparseable flow expressions: " + failures);
    }

    private static void parse(Runnable parse, JavaMethod method, String what, List<String> failures) {
        try {
            parse.run();
        } catch (ParseException e) {
            failures.add(method.getFullName() + " " + what + ": " + e.getMessage());
        }
    }

    private static DescribedPredicate<JavaMethod> callAnHttpClient() {
        return DescribedPredicate.describe("call RestClient, RestTemplate or WebClient", method ->
                method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> HTTP_CLIENTS.contains(call.getTargetOwner().getName().split("\\$")[0])));
    }

    private static ArchCondition<JavaMethod> notBeCalledFromOwnClass() {
        return new ArchCondition<>("not be called from its own class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getCallsOfSelf().stream()
                        .filter(call -> call.getOriginOwner().equals(method.getOwner()))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                "Self-invocation bypasses the flow interceptor: " + call.getDescription())));
            }
        };
    }

    private static ArchCondition<JavaMethod> onlyBeCalledFromSteps() {
        return new ArchCondition<>("only be called from @FlowStep methods") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getCallsOfSelf().stream()
                        .filter(call -> !call.getOrigin().isAnnotatedWith(FlowStep.class))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                "Decision called outside a @FlowStep method: " + call.getDescription())));
            }
        };
    }

    private static Stream<JavaMethod> flowMethods(JavaClasses classes) {
        return classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(FlowStep.class) || m.isAnnotatedWith(FlowDecision.class));
    }

    private static String flowName(JavaMethod m) {
        return m.isAnnotatedWith(FlowStep.class)
                ? m.getAnnotationOfType(FlowStep.class).action()
                : m.getAnnotationOfType(FlowDecision.class).question();
    }

    private static String participant(JavaMethod m) {
        return m.isAnnotatedWith(FlowStep.class)
                ? m.getAnnotationOfType(FlowStep.class).participant()
                : m.getAnnotationOfType(FlowDecision.class).participant();
    }

    private FlowDocsRules() {
    }
}
