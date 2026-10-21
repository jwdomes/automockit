package com.example.flowdocs.boot;

/** Names and tag keys of the observations the interceptor records. */
public final class FlowObservationKeys {

    public static final String OBSERVATION_NAME = "flow.step";
    public static final String STEP_KEY = "flow.step";
    public static final String PARTICIPANT_KEY = "flow.participant";
    public static final String KIND_KEY = "flow.kind";
    public static final String OUTCOME_KEY = "flow.outcome";
    public static final String REASON_KEY = "flow.reason";
    public static final String NOTE_EVENT = "note";

    public static final String KIND_STEP = "step";
    public static final String KIND_DECISION = "decision";

    private FlowObservationKeys() {
    }
}
