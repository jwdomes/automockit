package com.example.router.docs;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Builds the step tree as observations start and stop. Ignores observations without a flow.step key
 * (such as Boot's HTTP observations) but walks through them to find a step's parent.
 */
public class FlowRecorder {

    static final class Step {
        final Observation.Context context;
        final String action;
        final String participant;     // Participant enum name
        final long startSeq;
        volatile long stopSeq = Long.MAX_VALUE;
        final List<Step> children = new CopyOnWriteArrayList<>();

        Step(Observation.Context context, String action, String participant, long startSeq) {
            this.context = context;
            this.action = action;
            this.participant = participant;
            this.startSeq = startSeq;
        }
    }

    private final AtomicLong seq = new AtomicLong();
    private final Map<Object, Step> byContext = Collections.synchronizedMap(new IdentityHashMap<>());
    private final List<Step> roots = new CopyOnWriteArrayList<>();

    /** A field, not a bean, so Boot doesn't auto-register it a second time. */
    final ObservationHandler<Observation.Context> handler = new ObservationHandler<>() {
        @Override
        public boolean supportsContext(Observation.Context ctx) {
            return true;
        }

        @Override
        public void onStart(Observation.Context ctx) {
            KeyValue action = ctx.getLowCardinalityKeyValue(FlowStepObservationConfig.STEP_KEY);
            KeyValue participant = ctx.getLowCardinalityKeyValue(FlowStepObservationConfig.PARTICIPANT_KEY);
            if (action == null || participant == null) {
                return;
            }
            Step step = new Step(ctx, action.getValue(), participant.getValue(), seq.incrementAndGet());
            byContext.put(ctx, step);
            Step parent = nearestFlowParent(ctx);
            (parent == null ? roots : parent.children).add(step);
        }

        @Override
        public void onStop(Observation.Context ctx) {
            Step step = byContext.get(ctx);
            if (step != null) {
                step.stopSeq = seq.incrementAndGet();
            }
        }
    };

    public List<Step> roots() {
        return List.copyOf(roots);
    }

    public void clear() {
        roots.clear();
        byContext.clear();
        seq.set(0);
    }

    private Step nearestFlowParent(Observation.ContextView ctx) {
        ObservationView parent = ctx.getParentObservation();
        while (parent != null) {
            Step s = byContext.get(parent.getContextView());
            if (s != null) {
                return s;
            }
            parent = parent.getContextView().getParentObservation();
        }
        return null;
    }
}
