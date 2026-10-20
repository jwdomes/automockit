package com.example.router.docs;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotatedElementUtils;

@Configuration(proxyBeanMethods = false)
public class FlowStepObservationConfig {

    public static final String OBSERVATION_NAME = "flow.step";
    public static final String STEP_KEY = "flow.step";
    public static final String PARTICIPANT_KEY = "flow.participant";
    public static final String KIND_KEY = "flow.kind";
    public static final String OUTCOME_KEY = "flow.outcome";
    public static final String REASON_KEY = "flow.reason";

    public static final String KIND_STEP = "step";
    public static final String KIND_DECISION = "decision";

    /**
     * Off unless flow.observations.enabled=true. Without this bean, @FlowStep and @FlowDecision have no
     * runtime effect. Static with the infrastructure role so the auto-proxy creator picks it up early,
     * like @Transactional.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnProperty(name = "flow.observations.enabled", havingValue = "true")
    static Advisor flowStepAdvisor(ObjectProvider<ObservationRegistry> registry) {
        MethodInterceptor interceptor = invocation -> {
            Observation observation = observationFor(invocation.getMethod(), registry.getObject()).start();
            try (Observation.Scope scope = observation.openScope()) {
                Object result = invocation.proceed();
                if (result instanceof Decision decision) {
                    observation.lowCardinalityKeyValue(OUTCOME_KEY, decision.yes() ? "yes" : "no")
                            .highCardinalityKeyValue(REASON_KEY, decision.reason());
                }
                return result;
            } catch (Throwable e) {
                observation.error(e);
                throw e;
            } finally {
                observation.stop();
            }
        };
        ComposablePointcut pointcut = new ComposablePointcut(AnnotationMatchingPointcut.forMethodAnnotation(FlowStep.class))
                .union(AnnotationMatchingPointcut.forMethodAnnotation(FlowDecision.class));
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }

    private static Observation observationFor(Method method, ObservationRegistry registry) {
        FlowStep step = AnnotatedElementUtils.findMergedAnnotation(method, FlowStep.class);
        if (step != null) {
            return observation(registry, step.action(), step.participant(), KIND_STEP);
        }
        FlowDecision decision = AnnotatedElementUtils.findMergedAnnotation(method, FlowDecision.class);
        return observation(registry, decision.question(), decision.participant(), KIND_DECISION);
    }

    /** Every flow observation carries the same tag keys, which some metrics backends require. */
    private static Observation observation(ObservationRegistry registry, String name, Participant participant,
                                           String kind) {
        return Observation.createNotStarted(OBSERVATION_NAME, registry)
                .contextualName(name)
                .lowCardinalityKeyValue(STEP_KEY, name)
                .lowCardinalityKeyValue(PARTICIPANT_KEY, participant.name())
                .lowCardinalityKeyValue(KIND_KEY, kind)
                .lowCardinalityKeyValue(OUTCOME_KEY, "none");
    }
}
