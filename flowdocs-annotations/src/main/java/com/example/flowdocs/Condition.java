package com.example.flowdocs;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** One condition of a {@link FlowDecision}. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Condition {

    /** Short name shown in diagrams, e.g. "SSN present". */
    String label();

    /**
     * SpEL expression over the method's arguments ({@code #name}) that is true when the condition holds.
     * {@code #hasText(String)} is available.
     */
    String test();
}
