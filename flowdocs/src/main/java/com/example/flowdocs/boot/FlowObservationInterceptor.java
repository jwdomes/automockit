package com.example.flowdocs.boot;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowBranch;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
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
import org.springframework.core.env.PropertyResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;

/**
 * Records each {@link FlowStep} and {@link FlowDecision} call as a flow.step observation. Decisions keep
 * their real return value; their conditions are evaluated afterwards and cross-checked against it.
 * {@link FlowBranch}es on a step are resolved by watching which of their steps the call runs, and are
 * recorded as decision observations placed just before that step.
 */
public class FlowObservationInterceptor implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FlowObservationInterceptor.class);

    private final Supplier<ObservationRegistry> registry;
    private final MethodHandle property;
    private final boolean strict;
    private final Map<Method, FlowMethod> flowMethods = new ConcurrentHashMap<>();
    /**
     * Steps in progress on this thread that declare branches, innermost first. Static because it mirrors the
     * thread's call chain, which can cross beans advised by different interceptor instances.
     */
    private static final ThreadLocal<Deque<Frame>> frames = ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * @param properties the app's configuration, for {@code #property('key')} in expressions
     * @param strict     throw when conditions disagree with what happened or an expression fails (for tests);
     *                   otherwise log a warning and record the problem as the reason
     */
    public FlowObservationInterceptor(Supplier<ObservationRegistry> registry, PropertyResolver properties,
                                      boolean strict) {
        this.registry = registry;
        this.property = FlowExpressions.propertyFunction(properties);
        this.strict = strict;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object[] arguments = invocation.getArguments();
        FlowMethod flow = flowMethods.computeIfAbsent(method, FlowMethod::of);
        if (!flow.isDecision()) {
            resolveBranchesTakenBy(flow.name());
        }
        Observation observation = observation(flow.name(), flow.participant(), flow.kind()).start();
        Frame frame = flow.branches().isEmpty() ? null : enter(flow, observation, method, arguments);
        try (Observation.Scope scope = observation.openScope()) {
            if (flow.note() != null) {
                recordNote(observation, flow, method, arguments);
            }
            Object result = invocation.proceed();
            if (flow.isDecision()) {
                recordDecision(observation, flow, method, arguments, Boolean.TRUE.equals(result));
            }
            if (frame != null) {
                frame.branches().stream().filter(b -> b.taken == null).forEach(b -> resolve(frame, b, false));
            }
            return result;
        } catch (Throwable e) {
            observation.error(e);
            throw e;
        } finally {
            if (frame != null) {
                Deque<Frame> stack = frames.get();
                stack.remove(frame);
                if (stack.isEmpty()) {
                    frames.remove();
                }
            }
            observation.stop();
        }
    }

    private Observation observation(String name, String participant, String kind) {
        return Observation.createNotStarted(FlowObservationKeys.OBSERVATION_NAME, registry.get())
                .contextualName(name)
                .lowCardinalityKeyValue(FlowObservationKeys.STEP_KEY, name)
                .lowCardinalityKeyValue(FlowObservationKeys.PARTICIPANT_KEY, participant)
                .lowCardinalityKeyValue(FlowObservationKeys.KIND_KEY, kind)
                .lowCardinalityKeyValue(FlowObservationKeys.OUTCOME_KEY, "none");
    }

    private void recordNote(Observation observation, FlowMethod flow, Method method, Object[] arguments) {
        try {
            String text = flow.note().getValue(FlowExpressions.context(method, arguments, property), String.class);
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
            results = evaluate(flow.conditions(), FlowExpressions.context(method, arguments, property));
        } catch (EvaluationException e) {
            problem(observation, "condition error on " + flow.name() + ": " + e.getMessage(), e);
            return;
        }
        crossCheck(observation, flow.name(), results, yes);
    }

    /** On entry to a step with branches: evaluate each branch's conditions while the arguments are fresh. */
    private Frame enter(FlowMethod flow, Observation observation, Method method, Object[] arguments) {
        EvaluationContext context = FlowExpressions.context(method, arguments, property);
        List<BranchState> states = flow.branches().stream().map(branch -> {
            try {
                return new BranchState(branch, evaluate(branch.conditions(), context), null);
            } catch (EvaluationException e) {
                return new BranchState(branch, null, "condition error on " + branch.question() + ": " + e.getMessage());
            }
        }).toList();
        Frame frame = new Frame(observation, states);
        frames.get().push(frame);
        return frame;
    }

    /** A step is starting: it resolves any open branch, in any enclosing frame, whose path it is. */
    private void resolveBranchesTakenBy(String action) {
        for (Frame frame : frames.get()) {
            for (BranchState state : frame.branches()) {
                Branch branch = state.branch;
                boolean isYes = branch.gates().equals(action);
                boolean isNo = branch.otherwise().equals(action);
                if (!isYes && !isNo) {
                    continue;
                }
                if (state.taken == null) {
                    resolve(frame, state, isYes);
                } else if (state.taken != isYes) {
                    problem(null, "both paths of %s ran in one call".formatted(branch.question()), null);
                }
            }
        }
    }

    /** Records the branch as a zero-length decision observation under the step that declares it. */
    private void resolve(Frame frame, BranchState state, boolean yes) {
        state.taken = yes;
        Branch branch = state.branch;
        Observation decision = observation(branch.question(), branch.participant(), FlowObservationKeys.KIND_DECISION)
                .parentObservation(frame.observation())
                .lowCardinalityKeyValue(FlowObservationKeys.OUTCOME_KEY, yes ? "yes" : "no")
                .start();
        try {
            if (state.error != null) {
                problem(decision, state.error, null);
            } else if (!state.results.isEmpty()) {
                crossCheck(decision, branch.question(), state.results, yes);
            }
        } catch (IllegalStateException e) {
            decision.error(e);
            throw e;
        } finally {
            decision.stop();
        }
    }

    /** Stores the condition results and checks that they agree with the outcome. */
    private void crossCheck(Observation observation, String name, List<ConditionResults.ConditionResult> results,
                            boolean yes) {
        observation.getContext().put(ConditionResults.class, new ConditionResults(results));
        boolean allHeld = results.stream().allMatch(ConditionResults.ConditionResult::held);
        if (allHeld != yes) {
            problem(observation, "conditions disagree with %s: outcome %s, conditions %s".formatted(
                    name, yes ? "yes" : "no", describe(results)), null);
            return;
        }
        observation.highCardinalityKeyValue(FlowObservationKeys.REASON_KEY, yes
                ? results.stream().map(ConditionResults.ConditionResult::label).collect(Collectors.joining(" + "))
                : results.stream().filter(r -> !r.held()).findFirst().orElseThrow().label());
    }

    private static List<ConditionResults.ConditionResult> evaluate(List<NamedCondition> conditions,
                                                                   EvaluationContext context) {
        return conditions.stream()
                .map(c -> new ConditionResults.ConditionResult(c.label(), evaluate(c, context)))
                .toList();
    }

    private static boolean evaluate(NamedCondition condition, EvaluationContext context) {
        Boolean held = condition.test().getValue(context, Boolean.class);
        if (held == null) {
            throw new EvaluationException("Condition \"%s\" evaluated to null".formatted(condition.label()));
        }
        return held;
    }

    /** @param observation where to record the problem in lenient mode; null to only log it */
    private void problem(Observation observation, String message, Exception cause) {
        if (strict) {
            throw new IllegalStateException(message, cause);
        }
        log.warn(message, cause);
        if (observation != null) {
            observation.highCardinalityKeyValue(FlowObservationKeys.REASON_KEY, message);
        }
    }

    private static String describe(List<ConditionResults.ConditionResult> results) {
        return results.stream().map(r -> r.label() + "=" + r.held()).collect(Collectors.joining(", "));
    }

    record NamedCondition(String label, Expression test) {
    }

    record Branch(String question, String participant, String gates, String otherwise,
                  List<NamedCondition> conditions) {
    }

    record Frame(Observation observation, List<BranchState> branches) {
    }

    /** {@code results} is null when evaluation failed, with the failure in {@code error}. */
    static final class BranchState {
        final Branch branch;
        final List<ConditionResults.ConditionResult> results;
        final String error;
        Boolean taken;

        BranchState(Branch branch, List<ConditionResults.ConditionResult> results, String error) {
            this.branch = branch;
            this.results = results;
            this.error = error;
        }
    }

    /** Annotation data and parsed expressions, built once per method on first call. */
    record FlowMethod(String name, String participant, String kind, Expression note, List<NamedCondition> conditions,
                      List<Branch> branches) {

        boolean isDecision() {
            return FlowObservationKeys.KIND_DECISION.equals(kind);
        }

        static FlowMethod of(Method method) {
            FlowStep step = AnnotatedElementUtils.findMergedAnnotation(method, FlowStep.class);
            if (step != null) {
                List<Branch> branches = AnnotatedElementUtils.findMergedRepeatableAnnotations(method, FlowBranch.class)
                        .stream()
                        .map(b -> new Branch(b.question(), b.participant(), b.gates(), b.otherwise(), parse(b.conditions())))
                        .toList();
                return new FlowMethod(step.action(), step.participant(), FlowObservationKeys.KIND_STEP,
                        step.note().isEmpty() ? null : FlowExpressions.parseNote(step.note()), List.of(), branches);
            }
            FlowDecision decision = AnnotatedElementUtils.findMergedAnnotation(method, FlowDecision.class);
            return new FlowMethod(decision.question(), decision.participant(), FlowObservationKeys.KIND_DECISION,
                    null, parse(decision.conditions()), List.of());
        }

        private static List<NamedCondition> parse(Condition[] conditions) {
            return Arrays.stream(conditions)
                    .map(c -> new NamedCondition(c.label(), FlowExpressions.parseCondition(c.test())))
                    .toList();
        }
    }
}
