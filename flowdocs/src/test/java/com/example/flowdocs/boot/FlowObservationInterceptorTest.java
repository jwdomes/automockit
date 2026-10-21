package com.example.flowdocs.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowBranch;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.env.MockEnvironment;

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

    public static class Providers {

        @FlowStep(action = "Call new", participant = "NEW")
        public String callNew() {
            return "new";
        }

        @FlowStep(action = "Call legacy", participant = "LEGACY")
        public String callLegacy() {
            return "legacy";
        }
    }

    /** Inline if/else branches on configuration and on an argument, declared with @FlowBranch. */
    public static class Picker {

        private final Providers providers;

        public Picker(Providers providers) {
            this.providers = providers;
        }

        @FlowStep(action = "Pick", participant = "SYSTEM")
        @FlowBranch(question = "Use new?", participant = "SYSTEM", gates = "Call new", otherwise = "Call legacy",
                conditions = @Condition(label = "new enabled", test = "#property('use-new') == 'true'"))
        public String pick(boolean useNew) {
            if (useNew) {
                return providers.callNew();
            } else {
                return providers.callLegacy();
            }
        }

        @FlowStep(action = "Pick both", participant = "SYSTEM")
        @FlowBranch(question = "Both?", participant = "SYSTEM", gates = "Call new", otherwise = "Call legacy")
        public String pickBoth() {
            return providers.callNew() + providers.callLegacy();
        }

        @FlowStep(action = "Maybe", participant = "SYSTEM")
        @FlowBranch(question = "Call it?", participant = "SYSTEM", gates = "Call new",
                conditions = @Condition(label = "flag set", test = "#flag"))
        public void maybe(boolean flag) {
            if (flag) {
                providers.callNew();
            }
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
        return proxy(new Fixture(), strict, new MockEnvironment());
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, boolean strict, MockEnvironment environment) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(FlowDocsAutoConfiguration.advisor(() -> registry, environment, strict));
        return (T) factory.getProxy();
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
                .hasMessageContaining("conditions disagree with Always?: outcome yes");
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

    private Picker picker(boolean strict, String useNew) {
        MockEnvironment environment = new MockEnvironment().withProperty("use-new", useNew);
        return proxy(new Picker(proxy(new Providers(), strict, environment)), strict, environment);
    }

    private Observation.Context stoppedNamed(String name) {
        return stopped.stream().filter(c -> name.equals(c.getContextualName())).findFirst().orElseThrow();
    }

    @Test
    void branchIsNoWhenTheOtherwiseStepRunsAndIsRecordedJustBeforeIt() {
        assertThat(picker(true, "false").pick(false)).isEqualTo("legacy");

        assertThat(stopped).extracting(Observation.Context::getContextualName)
                .containsExactly("Use new?", "Call legacy", "Pick");
        Observation.Context branch = stoppedNamed("Use new?");
        assertThat(tag(branch, FlowObservationKeys.KIND_KEY)).isEqualTo("decision");
        assertThat(tag(branch, FlowObservationKeys.OUTCOME_KEY)).isEqualTo("no");
        assertThat(tag(branch, FlowObservationKeys.REASON_KEY)).isEqualTo("new enabled");
        assertThat(branch.getParentObservation().getContextView().getContextualName()).isEqualTo("Pick");
        ConditionResults conditions = branch.get(ConditionResults.class);
        assertThat(conditions.results()).containsExactly(new ConditionResults.ConditionResult("new enabled", false));
    }

    @Test
    void branchIsYesWhenTheGatedStepRuns() {
        assertThat(picker(true, "true").pick(true)).isEqualTo("new");

        assertThat(tag(stoppedNamed("Use new?"), FlowObservationKeys.OUTCOME_KEY)).isEqualTo("yes");
    }

    @Test
    void strictModeFailsWhenTheConfigurationDisagreesWithThePathTaken() {
        assertThatThrownBy(() -> picker(true, "true").pick(false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conditions disagree with Use new?: outcome no, conditions new enabled=true");
    }

    @Test
    void lenientModeRecordsTheDisagreementAndCarriesOn() {
        assertThat(picker(false, "true").pick(false)).isEqualTo("legacy");

        assertThat(tag(stoppedNamed("Use new?"), FlowObservationKeys.REASON_KEY)).startsWith("conditions disagree");
    }

    @Test
    void bothPathsRunningFailsInStrictMode() {
        assertThatThrownBy(() -> picker(true, "false").pickBoth())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both paths of Both? ran in one call");
    }

    @Test
    void anUntakenBranchWithoutOtherwiseIsNoAtExit() {
        picker(true, "false").maybe(false);

        assertThat(stopped).extracting(Observation.Context::getContextualName).containsExactly("Call it?", "Maybe");
        assertThat(tag(stoppedNamed("Call it?"), FlowObservationKeys.OUTCOME_KEY)).isEqualTo("no");
    }
}
