package com.example.router.docs;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

/** Adds a note to the flow step currently running. Does nothing when observations are off. */
@Component
public class FlowNotes {

    public static final String NOTE_EVENT = "note";

    private final ObservationRegistry registry;

    public FlowNotes(ObservationRegistry registry) {
        this.registry = registry;
    }

    public void note(String text) {
        Observation current = registry.getCurrentObservation();
        // Only flow steps: without this, notes would land on Boot's HTTP observations when flow observations are off.
        if (current != null
                && current.getContext().getLowCardinalityKeyValue(FlowStepObservationConfig.STEP_KEY) != null) {
            current.event(Observation.Event.of(NOTE_EVENT, text));
        }
    }
}
