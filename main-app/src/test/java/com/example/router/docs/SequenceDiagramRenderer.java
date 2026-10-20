package com.example.router.docs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A step on another participant gets a call and a return (--x with the exception name on failure),
 * a step on the same participant becomes a self-message, decisions and notes become notes, and
 * overlapping siblings form a par block.
 */
final class SequenceDiagramRenderer {

    private static final String UPSTREAM = "UPSTREAM";

    /**
     * The first root is the step that received the request. Later roots are exception handlers, which run
     * after it has closed; they render on its participant before the final arrow, which carries the status
     * the caller actually got.
     */
    String render(List<FlowRecorder.Step> roots, int status) {
        if (roots.isEmpty()) {
            throw new IllegalStateException("No flow steps recorded; is flow.observations.enabled set?");
        }
        List<FlowRecorder.Step> ordered = roots.stream().sorted(Comparator.comparingLong(s -> s.startSeq)).toList();
        FlowRecorder.Step entry = ordered.get(0);
        String receiver = entry.participant;
        List<String> participants = new ArrayList<>(List.of(UPSTREAM, receiver));
        StringBuilder body = new StringBuilder();
        String indent = "    ";

        body.append(indent).append("%s->>%s: %s\n".formatted(UPSTREAM, receiver, escape(entry.action)));
        renderChildren(entry, receiver, participants, body, indent);
        for (FlowRecorder.Step handler : ordered.subList(1, ordered.size())) {
            renderStep(handler, receiver, participants, body, indent);
        }
        body.append(indent).append("%s-->>%s: %d %s\n".formatted(
                receiver, UPSTREAM, status, HttpStatus.valueOf(status).getReasonPhrase()));

        StringBuilder out = new StringBuilder("sequenceDiagram\n");
        participants.forEach(p -> out.append("    participant %s as %s\n".formatted(p, displayName(p))));
        return out.append(body).toString();
    }

    private static String displayName(String participant) {
        return UPSTREAM.equals(participant) ? "Upstream" : Participant.valueOf(participant).label();
    }

    private void renderStep(FlowRecorder.Step step, String from, List<String> participants,
                            StringBuilder out, String indent) {
        String to = step.participant;
        if (!participants.contains(to)) {
            participants.add(to);
        }
        if (step.isDecision()) {
            out.append(indent).append("Note over %s: %s\n".formatted(
                    to, escape("%s %s — %s".formatted(step.action, step.outcome(), step.reason()))));
            return;
        }
        out.append(indent).append("%s->>%s: %s\n".formatted(from, to, escape(step.action)));
        renderChildren(step, to, participants, out, indent);
        if (!from.equals(to)) {
            Throwable error = step.context.getError();
            out.append(indent).append(error == null
                    ? "%s-->>%s: response\n".formatted(to, from)
                    : "%s--x%s: %s\n".formatted(to, from, error.getClass().getSimpleName()));
        }
    }

    /** Child steps and notes in sequence order; a note splits the children into separately grouped runs. */
    private void renderChildren(FlowRecorder.Step step, String participant, List<String> participants,
                                StringBuilder out, String indent) {
        List<FlowRecorder.Step> run = new ArrayList<>();
        List<FlowRecorder.Step> children = step.children.stream()
                .sorted(Comparator.comparingLong(s -> s.startSeq)).toList();
        int next = 0;
        for (FlowRecorder.Note note : step.notes) {
            while (next < children.size() && children.get(next).startSeq < note.seq()) {
                run.add(children.get(next++));
            }
            renderSiblings(run, participant, participants, out, indent);
            run.clear();
            out.append(indent).append("Note over %s: %s\n".formatted(participant, escape(note.text())));
        }
        renderSiblings(children.subList(next, children.size()), participant, participants, out, indent);
    }

    /** Runs of overlapping calls become a par block sorted by action. */
    private void renderSiblings(List<FlowRecorder.Step> ordered, String from, List<String> participants,
                                StringBuilder out, String indent) {
        int i = 0;
        while (i < ordered.size()) {
            List<FlowRecorder.Step> group = new ArrayList<>(List.of(ordered.get(i)));
            long groupEnd = ordered.get(i).stopSeq;
            int j = i + 1;
            while (j < ordered.size() && ordered.get(j).startSeq < groupEnd) {
                group.add(ordered.get(j));
                groupEnd = Math.max(groupEnd, ordered.get(j).stopSeq);
                j++;
            }
            if (group.size() == 1) {
                renderStep(group.get(0), from, participants, out, indent);
            } else {
                group.sort(Comparator.comparing(s -> s.action));
                for (int k = 0; k < group.size(); k++) {
                    out.append(indent).append(k == 0 ? "par " : "and ").append(escape(group.get(k).action)).append('\n');
                    renderStep(group.get(k), from, participants, out, indent + "    ");
                }
                out.append(indent).append("end\n");
            }
            i = j;
        }
    }

    private static String escape(String text) {
        return text.replace("\"", "#quot;").replace(";", "#59;");
    }
}
