package com.example.router.client;

import com.example.flowdocs.FlowStep;
import com.example.router.Participants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PartnerCClient {

    private final RestClient restClient;

    public PartnerCClient(RestClient.Builder builder, @Value("${partners.c.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @FlowStep(action = "Call Partner C", participant = Participants.PARTNER_C)
    public PartnerMatchResponse match(String requestId, PartnerCRequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
