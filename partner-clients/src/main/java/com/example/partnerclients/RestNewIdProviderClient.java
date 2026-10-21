package com.example.partnerclients;

import org.springframework.web.client.RestClient;

class RestNewIdProviderClient implements NewIdProviderClient {

    private final RestClient restClient;

    RestNewIdProviderClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public IdLookupResponse lookup(String requestId, IdLookupRequest request) {
        return restClient.post()
                .uri("/lookup")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(IdLookupResponse.class);
    }
}
