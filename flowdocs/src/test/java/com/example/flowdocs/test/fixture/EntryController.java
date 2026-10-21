package com.example.flowdocs.test.fixture;

import com.example.flowdocs.FlowStep;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntryController {

    private final Orchestrator orchestrator;

    public EntryController(Orchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @FlowStep(action = "Receive", participant = Ids.APP)
    public String receive(String in) {
        return orchestrator.run(in, in.startsWith("f"));
    }
}
