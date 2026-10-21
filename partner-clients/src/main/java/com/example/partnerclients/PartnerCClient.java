package com.example.partnerclients;

/** Client for Partner C's match API. */
public interface PartnerCClient {

    PartnerMatchResponse match(String requestId, PartnerCRequest request);
}
