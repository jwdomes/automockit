package com.example.router.docs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method returning {@link Decision} as a branch point in the request flow: a diamond in the
 * static graph and a note in scenario diagrams.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FlowDecision {

    /** The question being decided: node text and the decision's identity. */
    String question();

    /** Where: the system making the decision. */
    Participant participant();

    /** Action of the step that runs when the answer is yes. */
    String gates();
}
