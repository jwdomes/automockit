package com.example.flowdocs.test;

import com.example.flowdocs.boot.ConditionResults;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * A step on another participant gets a call and a return (--x with the exception name on failure), a
 * step on the same participant becomes a self-message, decisions and notes become notes, and overlapping
 * siblings form a par block.
 */
final class SequenceDiagramRenderer {

    private static final String UPSTREAM = "UPSTREAM";

    private final Map<String, String> labels;
    private final FlowDocsSettings settings;

    SequenceDiagramRenderer(Map<String, String> labels, FlowDocsSettings settings) {
        this.labels = labels;
        this.settings = settings;
    }

    /**
     * The first root received the request; later roots are exception handlers, which run after it has
     * closed and render on its participant before the final arrow. The final arrow carries the status the
     * caller actually got. With no roots, the request was rejected before any step ran.
     */
    String render(List<FlowRecorder.Step> roots, int status, String requestLine, Exception resolved) {
        String receiver = roots.isEmpty() ? settings.entryParticipant() : roots.get(0).participant;
        List<String> participants = new ArrayList<>(List.of(UPSTREAM, receiver));
        StringBuilder body = new StringBuilder();
        String indent = "    ";

        if (roots.isEmpty()) {
            body.append(indent).append("%s->>%s: %s\n".formatted(UPSTREAM, receiver, escape(requestLine)));
            body.append(indent).append("Note over %s: %s\n".formatted(receiver,
                    escape("Rejected before controller — " + rejection(resolved))));
        } else {
            FlowRecorder.Step entry = roots.get(0);
            renderNotes(entry, UPSTREAM, receiver, body, indent);
            body.append(indent).append("%s->>%s: %s\n".formatted(UPSTREAM, receiver, escape(entry.action)));
            renderSiblings(entry.children, receiver, participants, body, indent);
            for (FlowRecorder.Step handler : roots.subList(1, roots.size())) {
                renderStep(handler, receiver, participants, body, indent);
            }
        }
        body.append(indent).append("%s-->>%s: %d %s\n".formatted(
                receiver, UPSTREAM, status, HttpStatus.valueOf(status).getReasonPhrase()));

        StringBuilder out = new StringBuilder("sequenceDiagram\n");
        participants.forEach(p -> out.append("    participant %s as %s\n".formatted(p, escape(displayName(p)))));
        return out.append(body).toString();
    }

    static String rejection(Exception resolved) {
        if (resolved instanceof MethodArgumentNotValidException invalid) {
            return invalid.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
        return resolved == null ? "no flow steps recorded" : resolved.getClass().getSimpleName();
    }

    /** "Partner A eligible? no — SSN present ✓, valid passport ✗". */
    static String decisionText(FlowRecorder.Step decision) {
        ConditionResults conditions = decision.conditions();
        String detail = conditions != null
                ? conditions.results().stream()
                        .map(r -> r.label() + (r.held() ? " ✓" : " ✗"))
                        .collect(Collectors.joining(", "))
                : decision.reason();
        return detail == null ? decision.action + " " + decision.outcome()
                : decision.action + " " + decision.outcome() + " — " + detail;
    }

    private String displayName(String participant) {
        if (UPSTREAM.equals(participant)) {
            return settings.upstreamLabel();
        }
        String label = labels.get(participant);
        if (label == null) {
            throw new IllegalStateException("No @FlowParticipant declares id " + participant);
        }
        return label;
    }

    private void renderStep(FlowRecorder.Step step, String from, List<String> participants,
                            StringBuilder out, String indent) {
        String to = step.participant;
        if (!participants.contains(to)) {
            participants.add(to);
        }
        if (step.isDecision()) {
            out.append(indent).append("Note over %s: %s\n".formatted(to, escape(decisionText(step))));
            return;
        }
        renderNotes(step, from, to, out, indent);
        out.append(indent).append("%s->>%s: %s\n".formatted(from, to, escape(step.action)));
        renderSiblings(step.children, to, participants, out, indent);
        if (!from.equals(to)) {
            Throwable error = step.context.getError();
            out.append(indent).append(error == null
                    ? "%s-->>%s: response\n".formatted(to, from)
                    : "%s--x%s: %s\n".formatted(to, from, error.getClass().getSimpleName()));
        }
    }

    /** A step's notes are evaluated before it runs, so they render just before its call. */
    private static void renderNotes(FlowRecorder.Step step, String from, String to, StringBuilder out, String indent) {
        String over = from.equals(to) || UPSTREAM.equals(from) ? to : from + "," + to;
        step.notes.forEach(note -> out.append(indent).append("Note over %s: %s\n".formatted(over, escape(note.text()))));
    }

    /** Children in start order; runs of overlapping calls become a par block sorted by action. */
    private void renderSiblings(List<FlowRecorder.Step> children, String from, List<String> participants,
                                StringBuilder out, String indent) {
        List<FlowRecorder.Step> ordered = children.stream()
                .sorted(Comparator.comparingLong(s -> s.startSeq)).toList();
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
