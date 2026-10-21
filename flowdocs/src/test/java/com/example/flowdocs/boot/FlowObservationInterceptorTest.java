package com.example.flowdocs.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;

class FlowObservationInterceptorTest {

    public static class Fixture {

        @FlowDecision(question = "Eligible?", participant = "SYSTEM", gates = "Greet", conditions = {
                @Condition(label = "has name", test = "#hasText(#name)"),
                @Condition(label = "adult", test = "#age >= 18")})
        public boolean eligible(String name, int age) {
            return name != null && !name.isBlank() && age >= 18;
        }

        @FlowDecision(question = "Always?", participant = "SYSTEM", gates = "Greet", conditions = {
                @Condition(label = "has name", test = "#hasText(#name)"),
                @Condition(label = "adult", test = "#age >= 18")})
        public boolean disagrees(String name, int age) {
            return true;
        }

        @FlowDecision(question = "Broken?", participant = "SYSTEM", gates = "Greet", conditions =
                @Condition(label = "bad", test = "#missing.length() > 0"))
        public boolean broken(String name, int age) {
            return false;
        }

        @FlowStep(action = "Greet", participant = "SYSTEM", note = "Greeting #{#name}")
        public String greet(String name) {
            return "Hello " + name;
        }

        @FlowStep(action = "Fail", participant = "SYSTEM")
        public void fail() {
            throw new IllegalArgumentException("boom");
        }
    }

    private final List<Observation.Context> stopped = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final ObservationRegistry registry = ObservationRegistry.create();

    FlowObservationInterceptorTest() {
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onEvent(Observation.Event event, Observation.Context context) {
                notes.add(event.getContextualName());
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });
    }

    private Fixture proxy(boolean strict) {
        ProxyFactory factory = new ProxyFactory(new Fixture());
        factory.setProxyTargetClass(true);
        factory.addAdvisor(FlowDocsAutoConfiguration.advisor(() -> registry, strict));
        return (Fixture) factory.getProxy();
    }

    private Observation.Context only() {
        assertThat(stopped).hasSize(1);
        return stopped.get(0);
    }

    private static String tag(Observation.Context context, String key) {
        var value = context.getLowCardinalityKeyValue(key);
        return value != null ? value.getValue() : context.getHighCardinalityKeyValue(key).getValue();
    }

    @Test
    void yesDecisionRecordsOutcomeConditionsAndReason() {
        assertThat(proxy(true).eligible("Ada", 36)).isTrue();

        Observation.Context context = only();
        assertThat(tag(context, FlowObservationKeys.KIND_KEY)).isEqualTo("decision");
        assertThat(tag(context, FlowObservationKeys.STEP_KEY)).isEqualTo("Eligible?");
        assertThat(tag(context, FlowObservationKeys.OUTCOME_KEY)).isEqualTo("yes");
        assertThat(tag(context, FlowObservationKeys.REASON_KEY)).isEqualTo("has name + adult");
        ConditionResults conditions = context.get(ConditionResults.class);
        assertThat(conditions.results()).containsExactly(
                new ConditionResults.ConditionResult("has name", true),
                new ConditionResults.ConditionResult("adult", true));
    }

    @Test
    void noDecisionNamesTheFirstFailingCondition() {
        assertThat(proxy(true).eligible("Ada", 12)).isFalse();

        Observation.Context context = only();
        assertThat(tag(context, FlowObservationKeys.OUTCOME_KEY)).isEqualTo("no");
        assertThat(tag(context, FlowObservationKeys.REASON_KEY)).isEqualTo("adult");
    }

    @Test
    void strictModeFailsWhenConditionsDisagreeWithTheMethod() {
        assertThatThrownBy(() -> proxy(true).disagrees("", 12))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conditions disagree with Always?: returned true");
        assertThat(only().getError()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lenientModeKeepsTheRealResultAndRecordsTheDisagreement() {
        assertThat(proxy(false).disagrees("", 12)).isTrue();

        assertThat(tag(only(), FlowObservationKeys.REASON_KEY)).startsWith("conditions disagree");
    }

    @Test
    void conditionErrorsFailInStrictModeOnly() {
        assertThatThrownBy(() -> proxy(true).broken("Ada", 36))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("condition error on Broken?");

        stopped.clear();
        assertThat(proxy(false).broken("Ada", 36)).isFalse();
        assertThat(tag(only(), FlowObservationKeys.REASON_KEY)).startsWith("condition error");
    }

    @Test
    void stepRecordsNoteAndTags() {
        assertThat(proxy(true).greet("Ada")).isEqualTo("Hello Ada");

        Observation.Context context = only();
        assertThat(tag(context, FlowObservationKeys.KIND_KEY)).isEqualTo("step");
        assertThat(tag(context, FlowObservationKeys.OUTCOME_KEY)).isEqualTo("none");
        assertThat(tag(context, FlowObservationKeys.PARTICIPANT_KEY)).isEqualTo("SYSTEM");
        assertThat(notes).containsExactly("Greeting Ada");
    }

    @Test
    void stepErrorsAreRecordedAndRethrown() {
        assertThatThrownBy(() -> proxy(true).fail()).isInstanceOf(IllegalArgumentException.class);

        assertThat(only().getError()).hasMessage("boom");
    }
}
