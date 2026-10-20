package com.example.router.docs;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Renders docs/flows/<scenario>.md: request, decisions, diagram, partner calls and response. */
final class ScenarioMarkdown {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    String render(ScenarioReport r) {
        List<FlowRecorder.Step> steps = r.roots().stream().flatMap(FlowRecorder.Step::flatten).toList();
        String receiver = r.roots().get(0).participant;

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(r.name()).append("\n\n")
                .append(r.description()).append("\n\n")
                .append("**Status:** %d (expected %d) · **Partners called:** %s\n\n".formatted(
                        r.status(), r.expectedStatus(), partnersCalled(steps, receiver)));

        sb.append("## Request\n\n");
        details(sb, "POST /search", pretty(r.request()));

        sb.append("## Decisions\n\n");
        List<FlowRecorder.Step> decisions = steps.stream().filter(FlowRecorder.Step::isDecision).toList();
        if (decisions.isEmpty()) {
            sb.append("None reached.\n\n");
        } else {
            sb.append("| Decision | Outcome | Reason |\n| --- | --- | --- |\n");
            decisions.forEach(d -> sb.append("| %s | %s | %s |\n".formatted(d.action, d.outcome(), d.reason())));
            sb.append('\n');
        }

        sb.append("## Flow\n\n```mermaid\n").append(r.diagram()).append("```\n\n");

        sb.append("## Partner calls\n\n");
        if (r.partnerCalls().isEmpty()) {
            sb.append("None.\n\n");
        }
        for (ServeEvent call : r.partnerCalls()) {
            sb.append("### %s: %s %s → %d\n\n".formatted(partnerName(call.getRequest().getUrl()),
                    call.getRequest().getMethod(), call.getRequest().getUrl(), call.getResponse().getStatus()));
            sb.append(call.getWasMatched() ? "Matched `%s`.\n\n".formatted(stubName(r, call)) : "Matched no stub.\n\n");
            details(sb, "Request sent", prettyBody(call.getRequest().getBodyAsString()));
            details(sb, "Response returned", prettyBody(call.getResponse().getBodyAsString()));
        }

        List<ServeEvent> unmatched = r.partnerCalls().stream().filter(c -> !c.getWasMatched()).toList();
        if (!unmatched.isEmpty()) {
            sb.append("## Unmatched requests\n\n")
                    .append("> **Warning:** these partner calls matched no stub and got WireMock's 404.\n\n");
            unmatched.forEach(c -> sb.append("- %s %s\n".formatted(c.getRequest().getMethod(), c.getRequest().getUrl())));
            sb.append('\n');
        }

        sb.append("## Router response\n\n");
        details(sb, "%d %s".formatted(r.status(), HttpStatus.valueOf(r.status()).getReasonPhrase()),
                prettyBody(r.responseBody()));
        return sb.toString().stripTrailing() + "\n";
    }

    private static String partnersCalled(List<FlowRecorder.Step> steps, String receiver) {
        String called = steps.stream()
                .filter(s -> !s.isDecision() && !s.participant.equals(receiver))
                .map(s -> Participant.valueOf(s.participant).label())
                .distinct()
                .collect(Collectors.joining(", "));
        return called.isEmpty() ? "none" : called;
    }

    /** "/partner-a/matches" -> "Partner A". */
    private static String partnerName(String url) {
        String segment = url.split("/")[1];
        return Participant.valueOf(segment.toUpperCase().replace('-', '_')).label();
    }

    private static String stubName(ScenarioReport r, ServeEvent call) {
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
