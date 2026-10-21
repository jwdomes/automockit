package com.example.partnerclients;

import org.springframework.web.client.RestClient;

class RestPartnerBClient implements PartnerBClient {

    private final RestClient restClient;

    RestPartnerBClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PartnerMatchResponse match(String requestId, PartnerBRequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
