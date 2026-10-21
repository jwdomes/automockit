package com.example.partnerclients;

/** Client for the new ID provider, which resolves a person to a canonical person id. */
public interface NewIdProviderClient {

    IdLookupResponse lookup(String requestId, IdLookupRequest request);
}
