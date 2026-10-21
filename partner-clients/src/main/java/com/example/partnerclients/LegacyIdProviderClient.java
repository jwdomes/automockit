package com.example.partnerclients;

/** Client for the legacy ID provider, which resolves a person to a canonical person id. */
public interface LegacyIdProviderClient {

    IdLookupResponse lookup(String requestId, IdLookupRequest request);
}
