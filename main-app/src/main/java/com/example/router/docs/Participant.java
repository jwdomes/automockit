package com.example.router.docs;

/** Systems in the flow: subgraphs in the static graph, lifelines in scenario diagrams. */
public enum Participant {
    ROUTER("Router"),
    PARTNER_A("Partner A"),
    PARTNER_B("Partner B"),
    PARTNER_C("Partner C");

    private final String label;

    Participant(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
