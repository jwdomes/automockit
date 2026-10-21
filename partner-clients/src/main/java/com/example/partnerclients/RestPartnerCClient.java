package com.example.partnerclients;

import org.springframework.web.client.RestClient;

class RestPartnerCClient implements PartnerCClient {

    private final RestClient restClient;

    RestPartnerCClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PartnerMatchResponse match(String requestId, PartnerCRequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
