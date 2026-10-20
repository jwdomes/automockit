package com.example.router.client;

import com.example.router.docs.FlowStep;
import com.example.router.docs.Participant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PartnerBClient {

    private final RestClient restClient;

    public PartnerBClient(RestClient.Builder builder, @Value("${partners.b.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @FlowStep(action = "Call Partner B", participant = Participant.PARTNER_B, when = "PNumber + valid driver license")
    public PartnerMatchResponse match(String requestId, PartnerBRequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
