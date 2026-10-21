package com.example.flowdocs.test;

import com.example.flowdocs.boot.ConditionResults;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Renders flows/<scenario>.md: request, decisions, diagram, downstream calls and response. */
final class ScenarioMarkdown {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @param downstreamCalls WireMock serve events in arrival order
     * @param stubIds         ids of the scenario's stubs in registration order, so a match can be named stubs[i]
     */
    record Report(String name, Scenario scenario, int status, List<FlowRecorder.Step> roots, String diagram,
                  List<ServeEvent> downstreamCalls, List<UUID> stubIds, String responseBody) {
    }

    private final Map<String, String> labels;

    ScenarioMarkdown(Map<String, String> labels) {
        this.labels = labels;
    }

    String render(Report r) {
        List<FlowRecorder.Step> steps = r.roots().stream().flatMap(FlowRecorder.Step::flatten).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(r.name()).append("\n\n")
                .append(r.scenario().description()).append("\n\n")
                .append("**Status:** %d (expected %d) · **Called:** %s\n\n".formatted(
                        r.status(), r.scenario().expectedStatus(), called(r.roots(), steps)));

        sb.append("## Request\n\n");
        details(sb, r.scenario().method() + " " + r.scenario().path(), pretty(r.scenario().request()));

        sb.append("## Decisions\n\n");
        List<FlowRecorder.Step> decisions = steps.stream().filter(FlowRecorder.Step::isDecision).toList();
        if (decisions.isEmpty()) {
            sb.append("None reached.\n\n");
        } else {
            sb.append("| Decision | Outcome | Conditions |\n| --- | --- | --- |\n");
            decisions.forEach(d -> sb.append("| %s | %s | %s |\n".formatted(d.action, d.outcome(), conditions(d))));
            sb.append('\n');
        }

        sb.append("## Flow\n\n```mermaid\n").append(r.diagram()).append("```\n\n");

        sb.append("## Downstream calls\n\n");
        if (r.downstreamCalls().isEmpty()) {
            sb.append("None.\n\n");
        }
        for (ServeEvent call : r.downstreamCalls()) {
            sb.append("### %s %s → %d\n\n".formatted(
                    call.getRequest().getMethod(), call.getRequest().getUrl(), call.getResponse().getStatus()));
            sb.append(call.getWasMatched() ? "Matched `%s`.\n\n".formatted(stubName(r, call)) : "Matched no stub.\n\n");
            details(sb, "Request sent", prettyBody(call.getRequest().getBodyAsString()));
            details(sb, "Response returned", prettyBody(call.getResponse().getBodyAsString()));
        }

        List<ServeEvent> unmatched = r.downstreamCalls().stream().filter(c -> !c.getWasMatched()).toList();
        if (!unmatched.isEmpty()) {
            sb.append("## Unmatched requests\n\n")
                    .append("> **Warning:** these downstream calls matched no stub and got WireMock's 404.\n\n");
            unmatched.forEach(c -> sb.append("- %s %s\n".formatted(c.getRequest().getMethod(), c.getRequest().getUrl())));
            sb.append('\n');
        }

        sb.append("## Response\n\n");
        details(sb, "%d %s".formatted(r.status(), HttpStatus.valueOf(r.status()).getReasonPhrase()),
                prettyBody(r.responseBody()));
        return sb.toString().stripTrailing() + "\n";
    }

    /** Participants other than the one that received the request, in call order. */
    private String called(List<FlowRecorder.Step> roots, List<FlowRecorder.Step> steps) {
        if (roots.isEmpty()) {
            return "none";
        }
        String receiver = roots.get(0).participant;
        String called = steps.stream()
                .filter(s -> !s.isDecision() && !s.participant.equals(receiver))
                .map(s -> labels.getOrDefault(s.participant, s.participant))
                .distinct()
                .collect(Collectors.joining(", "));
        return called.isEmpty() ? "none" : called;
    }

    private static String conditions(FlowRecorder.Step decision) {
        ConditionResults results = decision.conditions();
        if (results == null) {
            return decision.reason() == null ? "" : decision.reason();
        }
        return results.results().stream()
                .map(c -> c.label() + (c.held() ? " ✓" : " ✗"))
                .collect(Collectors.joining(", "));
    }

    private static String stubName(Report r, ServeEvent call) {
        int index = r.stubIds().indexOf(call.getStubMapping().getId());
        return index < 0 ? call.getStubMapping().getId().toString() : "stubs[%d]".formatted(index);
    }

    /** A null body renders as plain text rather than an empty code block. */
    private static void details(StringBuilder sb, String summary, String json) {
        sb.append("<details><summary>").append(summary).append("</summary>\n\n")
                .append(json == null ? "_Empty body._\n" : "```json\n" + json + "\n```\n")
                .append("\n</details>\n\n");
    }

    private static String prettyBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.strip();
        return trimmed.startsWith("{") || trimmed.startsWith("[") ? pretty(JSON.readTree(trimmed)) : trimmed;
    }

    private static String pretty(JsonNode node) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(node);
    }
}
