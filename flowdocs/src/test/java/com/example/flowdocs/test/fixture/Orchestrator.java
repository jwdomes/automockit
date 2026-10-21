package com.example.flowdocs.test.fixture;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowBranch;
import com.example.flowdocs.FlowStep;

public class Orchestrator {

    private final Checks checks;
    private final Backend backend;

    public Orchestrator(Checks checks, Backend backend) {
        this.checks = checks;
        this.backend = backend;
    }

    @FlowStep(action = "Orchestrate", participant = Ids.APP)
    @FlowBranch(question = "Fast?", participant = Ids.APP, gates = "Call fast", otherwise = "Call slow",
            conditions = @Condition(label = "fast flag", test = "#fast"))
    public String run(String in, boolean fast) {
        if (fast) {
            backend.callFast(in);
        } else {
            backend.callSlow(in);
        }
        if (checks.first(in)) {
            backend.callOne(in);
        }
        if (checks.second(in)) {
            backend.callTwo(in);
        }
        return in;
    }
}
