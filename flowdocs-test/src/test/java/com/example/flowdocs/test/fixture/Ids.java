package com.example.flowdocs.test.fixture;

import com.example.flowdocs.FlowParticipant;

public final class Ids {

    @FlowParticipant(label = "App")
    public static final String APP = "APP";

    @FlowParticipant(label = "Backend")
    public static final String BACKEND = "BACKEND";

    private Ids() {
    }
}
