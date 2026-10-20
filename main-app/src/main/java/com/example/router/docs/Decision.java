package com.example.router.docs;

/** Outcome of a {@link FlowDecision} method, with the reason recorded in the flow docs. */
public record Decision(boolean yes, String reason) {

    public static Decision yes(String reason) {
        return new Decision(true, reason);
    }

    public static Decision no(String reason) {
        return new Decision(false, reason);
    }
}
