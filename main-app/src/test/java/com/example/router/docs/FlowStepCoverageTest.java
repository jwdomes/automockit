package com.example.router.docs;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
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

@AnalyzeClasses(packages = "com.example.router", importOptions = ImportOption.DoNotIncludeTests.class)
class FlowStepCoverageTest {

    // Scoped to *Client classes: the client package also holds request records with public accessors.
    @ArchTest
    static final ArchRule downstreamCallsAreSteps =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..client..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Client")
                    .and().arePublic()
                    .should().beAnnotatedWith(FlowStep.class);

    /** Steps and decisions share one id namespace in the static graph. */
    @ArchTest
    static void actionsAreUnique(JavaClasses classes) {
        Map<String, List<String>> bySlug = new TreeMap<>();
        flowMethods(classes).forEach(m -> bySlug
                .computeIfAbsent(FlowDocsTest.slug(flowName(m)), k -> new ArrayList<>())
                .add(m.getFullName()));
        bySlug.values().removeIf(methods -> methods.size() < 2);
        assertTrue(bySlug.isEmpty(), "Duplicate @FlowStep actions or @FlowDecision questions: " + bySlug);
    }

    @ArchTest
    static final ArchRule methodsReturningDecisionAreDecisions =
            methods().that().haveRawReturnType(Decision.class)
                    .and().areNotDeclaredIn(Decision.class)
                    .should().beAnnotatedWith(FlowDecision.class);

    @ArchTest
    static void decisionTargetsExist(JavaClasses classes) {
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

    /** The static graph orders decisions by call site within a step, so they must be called from one. */
    @ArchTest
    static final ArchRule decisionsAreCalledFromSteps =
            methods().that().areAnnotatedWith(FlowDecision.class).should(onlyBeCalledFromSteps());

    @ArchTest
    static final ArchRule stepsAreProxyable =
            methods().that().areAnnotatedWith(FlowStep.class).or().areAnnotatedWith(FlowDecision.class)
                    .should().notHaveModifier(JavaModifier.PRIVATE)
                    .andShould().notHaveModifier(JavaModifier.FINAL);

    @ArchTest
    static final ArchRule noSelfInvocation =
            methods().that().areAnnotatedWith(FlowStep.class).or().areAnnotatedWith(FlowDecision.class)
                    .should(notBeCalledFromOwnClass());

    private static ArchCondition<JavaMethod> notBeCalledFromOwnClass() {
        return new ArchCondition<>("not be called from its own class") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                method.getCallsOfSelf().stream()
                        .filter(call -> call.getOriginOwner().equals(method.getOwner()))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                "Self-invocation bypasses the @FlowStep interceptor: " + call.getDescription())));
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
}
