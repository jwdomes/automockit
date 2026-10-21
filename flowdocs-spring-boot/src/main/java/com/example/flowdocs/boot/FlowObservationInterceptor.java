package com.example.flowdocs.boot;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;

/**
 * Records each {@link FlowStep} and {@link FlowDecision} call as a flow.step observation. Decisions keep
 * their real return value; their conditions are evaluated afterwards and cross-checked against it.
 */
public class FlowObservationInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowObservationInterceptor.class);

    private final Supplier<ObservationRegistry> registry;
    private final boolean strict;
    private final Map<Method, FlowMethod> flowMethods = new ConcurrentHashMap<>();

    /**
     * @param strict throw when conditions disagree with a decision or an expression fails (for tests);
     *               otherwise log a warning and record the problem as the decision's reason
     */
    public FlowObservationInterceptor(Supplier<ObservationRegistry> registry, boolean strict) {
        this.registry = registry;
        this.strict = strict;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        FlowMethod flow = flowMethods.computeIfAbsent(method, FlowMethod::of);
        Observation observation = Observation.createNotStarted(FlowObservationKeys.OBSERVATION_NAME, registry.get())
                .contextualName(flow.name())
                .lowCardinalityKeyValue(FlowObservationKeys.STEP_KEY, flow.name())
                .lowCardinalityKeyValue(FlowObservationKeys.PARTICIPANT_KEY, flow.participant())
                .lowCardinalityKeyValue(FlowObservationKeys.KIND_KEY, flow.kind())
                .lowCardinalityKeyValue(FlowObservationKeys.OUTCOME_KEY, "none")
                .start();
        try (Observation.Scope scope = observation.openScope()) {
            if (flow.note() != null) {
                recordNote(observation, flow, method, invocation.getArguments());
            }
            Object result = invocation.proceed();
            if (flow.isDecision()) {
                recordDecision(observation, flow, method, invocation.getArguments(), Boolean.TRUE.equals(result));
            }
            return result;
        } catch (Throwable e) {
            observation.error(e);
            throw e;
        } finally {
            observation.stop();
        }
    }

    private void recordNote(Observation observation, FlowMethod flow, Method method, Object[] arguments) {
        try {
            String text = flow.note().getValue(FlowExpressions.context(method, arguments), String.class);
            observation.event(Observation.Event.of(FlowObservationKeys.NOTE_EVENT, text));
        } catch (EvaluationException e) {
            problem(observation, "note error on " + flow.name() + ": " + e.getMessage(), e);
        }
    }

    private void recordDecision(Observation observation, FlowMethod flow, Method method, Object[] arguments,
                                boolean yes) {
        observation.lowCardinalityKeyValue(FlowObservationKeys.OUTCOME_KEY, yes ? "yes" : "no");
        if (flow.conditions().isEmpty()) {
            return;
        }
        List<ConditionResults.ConditionResult> results;
        try {
            EvaluationContext context = FlowExpressions.context(method, arguments);
            results = flow.conditions().stream()
                    .map(c -> new ConditionResults.ConditionResult(c.label(), evaluate(c, context)))
                    .toList();
        } catch (EvaluationException e) {
            problem(observation, "condition error on " + flow.name() + ": " + e.getMessage(), e);
            return;
        }
        observation.getContext().put(ConditionResults.class, new ConditionResults(results));

        boolean allHeld = results.stream().allMatch(ConditionResults.ConditionResult::held);
        if (allHeld != yes) {
            problem(observation, "conditions disagree with %s: returned %s, conditions %s".formatted(
                    flow.name(), yes, describe(results)), null);
            return;
        }
        observation.highCardinalityKeyValue(FlowObservationKeys.REASON_KEY, yes
                ? results.stream().map(ConditionResults.ConditionResult::label).collect(Collectors.joining(" + "))
                : results.stream().filter(r -> !r.held()).findFirst().orElseThrow().label());
    }

    private static boolean evaluate(NamedCondition condition, EvaluationContext context) {
        Boolean held = condition.test().getValue(context, Boolean.class);
        if (held == null) {
            throw new EvaluationException("Condition \"%s\" evaluated to null".formatted(condition.label()));
        }
        return held;
    }

    private void problem(Observation observation, String message, Exception cause) {
        if (strict) {
            throw new IllegalStateException(message, cause);
        }
        log.warn(message, cause);
        observation.highCardinalityKeyValue(FlowObservationKeys.REASON_KEY, message);
    }

    private static String describe(List<ConditionResults.ConditionResult> results) {
        return results.stream().map(r -> r.label() + "=" + r.held()).collect(Collectors.joining(", "));
    }

    record NamedCondition(String label, Expression test) {
    }

    /** Annotation data and parsed expressions, built once per method on first call. */
    record FlowMethod(String name, String participant, String kind, Expression note, List<NamedCondition> conditions) {

        boolean isDecision() {
            return FlowObservationKeys.KIND_DECISION.equals(kind);
        }

        static FlowMethod of(Method method) {
            FlowStep step = AnnotatedElementUtils.findMergedAnnotation(method, FlowStep.class);
            if (step != null) {
                return new FlowMethod(step.action(), step.participant(), FlowObservationKeys.KIND_STEP,
                        step.note().isEmpty() ? null : FlowExpressions.parseNote(step.note()), List.of());
            }
            FlowDecision decision = AnnotatedElementUtils.findMergedAnnotation(method, FlowDecision.class);
            List<NamedCondition> conditions = Arrays.stream(decision.conditions())
                    .map((Condition c) -> new NamedCondition(c.label(), FlowExpressions.parseCondition(c.test())))
                    .toList();
            return new FlowMethod(decision.question(), decision.participant(), FlowObservationKeys.KIND_DECISION,
                    null, conditions);
        }
    }
}
