package com.example.router.docs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A step on another participant gets a call and a return (--x with the exception name on failure),
 * a step on the same participant becomes a self-message, and overlapping siblings form a par block.
 */
final class SequenceDiagramRenderer {

    private static final String UPSTREAM = "UPSTREAM";

    String render(List<FlowRecorder.Step> roots) {
        List<String> participants = new ArrayList<>(List.of(UPSTREAM));
        StringBuilder body = new StringBuilder();
        renderSiblings(roots, UPSTREAM, participants, body, "    ");
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
        out.append(indent).append("%s->>%s: %s\n".formatted(from, to, text(step)));
        renderSiblings(step.children, to, participants, out, indent);
        if (!from.equals(to)) {
            Throwable error = step.context.getError();
            out.append(indent).append(error == null
                    ? "%s-->>%s: response\n".formatted(to, from)
                    : "%s--x%s: %s\n".formatted(to, from, error.getClass().getSimpleName()));
        }
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
                    out.append(indent).append(k == 0 ? "par " : "and ").append(text(group.get(k))).append('\n');
                    renderStep(group.get(k), from, participants, out, indent + "    ");
                }
                out.append(indent).append("end\n");
            }
            i = j;
        }
    }

    private static String text(FlowRecorder.Step step) {
        return step.action.replace("\"", "#quot;").replace(";", "#59;");
    }
}
