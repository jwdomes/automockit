package com.example.router.docs;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
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

    /**
     * Off unless flow.observations.enabled=true. Without this bean, @FlowStep has no runtime effect.
     * Static with the infrastructure role so the auto-proxy creator picks it up early, like @Transactional.
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnProperty(name = "flow.observations.enabled", havingValue = "true")
    static Advisor flowStepAdvisor(ObjectProvider<ObservationRegistry> registry) {
        MethodInterceptor interceptor = invocation -> {
            FlowStep step = AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(), FlowStep.class);
            return Observation.createNotStarted(OBSERVATION_NAME, registry.getObject())
                    .contextualName(step.action())
                    .lowCardinalityKeyValue(STEP_KEY, step.action())
                    .lowCardinalityKeyValue(PARTICIPANT_KEY, step.participant().name())
                    .observeChecked(invocation::proceed);
        };
        return new DefaultPointcutAdvisor(
                AnnotationMatchingPointcut.forMethodAnnotation(FlowStep.class), interceptor);
    }
}
