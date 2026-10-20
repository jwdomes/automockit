package com.example.router.docs;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a step in the request flow. Read by ArchUnit at build time and by the interceptor at runtime. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FlowStep {

    /** What is happening: node and message text, and the step's identity. */
    String action();

    /** Where: the system doing the work. */
    Participant participant();
}
