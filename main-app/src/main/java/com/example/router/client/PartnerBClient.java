package com.example.router.client;

import com.example.flowdocs.FlowStep;
import com.example.router.Participants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PartnerBClient {

    private final RestClient restClient;

    public PartnerBClient(RestClient.Builder builder, @Value("${partners.b.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    @FlowStep(action = "Call Partner B", participant = Participants.PARTNER_B,
            note = "Using driver license #{#request.documentNumber()}")
    public PartnerMatchResponse match(String requestId, PartnerBRequest request) {
        return restClient.post()
                .uri("/matches")
                .header("X-Request-Id", requestId)
                .body(request)
                .retrieve()
                .body(PartnerMatchResponse.class);
    }
}
