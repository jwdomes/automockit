package com.example.partnerclients;

/** Client for Partner A's match API. */
public interface PartnerAClient {

    PartnerMatchResponse match(String requestId, PartnerARequest request);
}
