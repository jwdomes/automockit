package com.example.router.docs;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Everything one scenario run produced, for its markdown page. */
record ScenarioReport(
        String name,
        String description,
        int expectedStatus,
        int status,
        JsonNode request,
        List<FlowRecorder.Step> roots,
        String diagram,
        List<ServeEvent> partnerCalls,   // arrival order
        List<UUID> stubIds,              // registration order, so a match can be named stubs[i]
        String responseBody) {
}
