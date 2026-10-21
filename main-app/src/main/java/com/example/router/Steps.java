package com.example.router;

/**
 * Step actions for @FlowStep, and for the gates and otherwise of @FlowDecision and @FlowBranch. The action
 * text is the step's identity in the flow docs, so a rename here renames it everywhere at once.
 */
public final class Steps {

    public static final String RECEIVE_SEARCH_REQUEST = "Receive search request";
    public static final String DETERMINE_DOWNSTREAM_PARTNERS = "Determine downstream partners";
    public static final String CALL_NEW_ID_PROVIDER = "Call New ID Provider";
    public static final String CALL_LEGACY_ID_PROVIDER = "Call Legacy ID Provider";
    public static final String CALL_PARTNER_A = "Call Partner A";
    public static final String CALL_PARTNER_B = "Call Partner B";
    public static final String CALL_PARTNER_C = "Call Partner C";
    public static final String MAP_PARTNER_FAILURE_TO_502 = "Map partner failure to 502";

    private Steps() {
    }
}
