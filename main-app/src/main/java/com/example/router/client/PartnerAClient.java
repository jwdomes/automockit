package com.example.router.client;

import com.example.router.docs.FlowStep;
import com.example.router.docs.Participant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PartnerAClient {

    private final RestClient restClient;

    public PartnerAClient(RestClient.Builder builder, @Value("${partners.a.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @FlowStep(action = "Call Partner A", participant = Participant.PARTNER_A)
    public PartnerMatchResponse match(String requestId, PartnerARequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
