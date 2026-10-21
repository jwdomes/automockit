package com.example.flowdocs.test.badgate;

import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;

/** "Ask" decides "Allowed?", which gates "Elsewhere" — a step "Ask" never reaches. */
public class BadGate {

    private final Checks checks;

    public BadGate(Checks checks) {
        this.checks = checks;
    }

    @FlowStep(action = "Ask", participant = "APP")
    public boolean ask(String in) {
        return checks.allowed(in);
    }

    public static class Checks {

        @FlowDecision(question = "Allowed?", participant = "APP", gates = "Elsewhere")
        public boolean allowed(String in) {
            return true;
        }
    }

    public static class Unrelated {

        @FlowStep(action = "Elsewhere", participant = "APP")
        public void elsewhere() {
        }
    }
}
