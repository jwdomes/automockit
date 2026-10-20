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
import java.util.TreeMap;

@AnalyzeClasses(packages = "com.example.router", importOptions = ImportOption.DoNotIncludeTests.class)
class FlowStepCoverageTest {

    // Scoped to *Client classes: the client package also holds request records with public accessors.
    @ArchTest
    static final ArchRule downstreamCallsAreSteps =
            methods().that().areDeclaredInClassesThat().resideInAPackage("..client..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Client")
                    .and().arePublic()
                    .should().beAnnotatedWith(FlowStep.class);

    @ArchTest
    static void actionsAreUnique(JavaClasses classes) {
        Map<String, List<String>> bySlug = new TreeMap<>();
        classes.stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.isAnnotatedWith(FlowStep.class))
                .forEach(m -> bySlug
                        .computeIfAbsent(FlowDocsTest.slug(m.getAnnotationOfType(FlowStep.class).action()),
                                k -> new ArrayList<>())
                        .add(m.getFullName()));
        bySlug.values().removeIf(methods -> methods.size() < 2);
        assertTrue(bySlug.isEmpty(), "Duplicate @FlowStep actions: " + bySlug);
    }

    @ArchTest
    static final ArchRule stepsAreProxyable =
            methods().that().areAnnotatedWith(FlowStep.class)
                    .should().notHaveModifier(JavaModifier.PRIVATE)
                    .andShould().notHaveModifier(JavaModifier.FINAL);

    @ArchTest
    static final ArchRule noSelfInvocation =
            methods().that().areAnnotatedWith(FlowStep.class).should(notBeCalledFromOwnClass());

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
}
