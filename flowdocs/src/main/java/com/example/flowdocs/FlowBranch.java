package com.example.flowdocs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an inline branch (an if/else in the method body) on the {@link FlowStep} method that contains
 * it, for branches with no method of their own to put {@link FlowDecision} on. The outcome is observed:
 * yes when the {@link #gates()} step runs during the call, no when the {@link #otherwise()} step does.
 * {@link #conditions()} restate the branch's test and are evaluated on entry; with
 * flow.observations.strict=true, conditions that disagree with what ran fail the call.
 */
@Documented
@Repeatable(FlowBranches.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FlowBranch {

    /** The question being decided: the branch's title and identity. */
    String question();

    /** Id of the system making the decision, declared by a {@link FlowParticipant} constant. */
    String participant();

    /** Action of the {@link FlowStep} that runs when the answer is yes. */
    String gates();

    /** Action of the {@link FlowStep} that runs when the answer is no (the else), if there is one. */
    String otherwise() default "";

    /**
     * Conditions that must all hold for a yes. Besides the method's arguments ({@code #name}),
     * {@code #property('key')} reads the app's configuration.
     */
    Condition[] conditions() default {};
}
