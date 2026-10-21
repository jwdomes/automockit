package com.example.flowdocs.test.fixture;

import com.example.flowdocs.FlowStep;

public class Backend {

    @FlowStep(action = "Call one", participant = Ids.BACKEND)
    public void callOne(String in) {
    }

    @FlowStep(action = "Call two", participant = Ids.BACKEND)
    public void callTwo(String in) {
    }
}
