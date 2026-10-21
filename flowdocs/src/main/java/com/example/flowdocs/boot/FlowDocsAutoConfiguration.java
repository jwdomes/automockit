package com.example.flowdocs.boot;

import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;

/**
 * Registers nothing unless flow.observations.enabled=true, so @FlowStep and @FlowDecision stay inert
 * metadata in production: no beans, no proxies, no expression parsing. When enabled, an
 * ObservationRegistry bean is required (Actuator provides one).
 */
@AutoConfiguration
@ConditionalOnProperty(name = "flow.observations.enabled", havingValue = "true")
public class FlowDocsAutoConfiguration {

    /** Static with the infrastructure role so the auto-proxy creator picks it up early, like @Transactional. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor flowObservationAdvisor(ObjectProvider<ObservationRegistry> registry, Environment environment) {
        return advisor(registry::getObject, environment,
                environment.getProperty("flow.observations.strict", Boolean.class, false));
    }

    static Advisor advisor(Supplier<ObservationRegistry> registry, PropertyResolver properties, boolean strict) {
        ComposablePointcut pointcut = new ComposablePointcut(AnnotationMatchingPointcut.forMethodAnnotation(FlowStep.class))
                .union(AnnotationMatchingPointcut.forMethodAnnotation(FlowDecision.class));
        return new DefaultPointcutAdvisor(pointcut, new FlowObservationInterceptor(registry, properties, strict));
    }
}
