package com.example.partnerclients;

import org.springframework.web.client.RestClient;

class RestPartnerAClient implements PartnerAClient {

    private final RestClient restClient;

    RestPartnerAClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PartnerMatchResponse match(String requestId, PartnerARequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
