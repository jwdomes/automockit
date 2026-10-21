package com.example.partnerclients;

/** Client for Partner B's match API. */
public interface PartnerBClient {

    PartnerMatchResponse match(String requestId, PartnerBRequest request);
}
