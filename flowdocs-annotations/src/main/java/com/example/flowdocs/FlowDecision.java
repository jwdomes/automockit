package com.example.flowdocs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a boolean method as a branch point in the request flow. The method itself is untouched: its
 * return value is the outcome, and {@link #conditions()} restate what it checks so the docs can show
 * which condition decided it. With flow.observations.strict=true, conditions that disagree with the
 * actual return value fail the call.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FlowDecision {

    /** The question being decided: the decision's title and identity. */
    String question();

    /** Id of the system making the decision, declared by a {@link FlowParticipant} constant. */
    String participant();

    /** Action of the {@link FlowStep} that runs when the answer is yes. */
    String gates();

    /** Conditions that must all hold for a yes, in the order the method checks them. */
    Condition[] conditions() default {};
}
