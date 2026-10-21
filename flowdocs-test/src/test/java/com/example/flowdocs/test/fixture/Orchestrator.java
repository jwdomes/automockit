package com.example.flowdocs.test.fixture;

import com.example.flowdocs.FlowStep;

public class Orchestrator {

    private final Checks checks;
    private final Backend backend;

    public Orchestrator(Checks checks, Backend backend) {
        this.checks = checks;
        this.backend = backend;
    }

    @FlowStep(action = "Orchestrate", participant = Ids.APP)
    public String run(String in) {
        if (checks.first(in)) {
            backend.callOne(in);
        }
        if (checks.second(in)) {
            backend.callTwo(in);
        }
        return in;
    }
}
