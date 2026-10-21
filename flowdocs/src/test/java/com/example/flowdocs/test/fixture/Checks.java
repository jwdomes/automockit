package com.example.flowdocs.test.fixture;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;

public class Checks {

    @FlowDecision(question = "First?", participant = Ids.APP, gates = "Call one", conditions = {
            @Condition(label = "not empty", test = "!#in.isEmpty()"),
            @Condition(label = "short", test = "#in.length() < 5")})
    public boolean first(String in) {
        return !in.isEmpty() && in.length() < 5;
    }

    @FlowDecision(question = "Second?", participant = Ids.APP, gates = "Call two")
    public boolean second(String in) {
        return true;
    }
}
