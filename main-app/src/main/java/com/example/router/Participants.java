package com.example.router;

import com.example.flowdocs.FlowParticipant;

/** Participant ids for @FlowStep and @FlowDecision; the labels appear in the flow docs. */
public final class Participants {

    @FlowParticipant(label = "Router")
    public static final String ROUTER = "ROUTER";

    @FlowParticipant(label = "Partner A")
    public static final String PARTNER_A = "PARTNER_A";

    @FlowParticipant(label = "Partner B")
    public static final String PARTNER_B = "PARTNER_B";

    @FlowParticipant(label = "Partner C")
    public static final String PARTNER_C = "PARTNER_C";

    private Participants() {
    }
}
