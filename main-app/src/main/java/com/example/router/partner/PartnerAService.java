package com.example.router.partner;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.router.Participants;
import com.example.router.client.PartnerAClient;
import com.example.router.client.PartnerARequest;
import com.example.router.dto.DocumentType;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.Person;
import com.example.router.dto.RequestDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PartnerAService {

    private final PartnerAClient client;

    public PartnerAService(PartnerAClient client) {
        this.client = client;
    }

    @FlowDecision(question = "Partner A eligible?", participant = Participants.ROUTER, gates = "Call Partner A",
            conditions = {
                    @Condition(label = "SSN present", test = "#hasText(#request.person().ssn())"),
                    @Condition(label = "valid passport", test =
                            "#request.firstValidNumber(T(com.example.router.dto.DocumentType).PASSPORT).isPresent()")})
    public boolean shouldCall(RequestDto request) {
        return StringUtils.hasText(request.person().ssn())
                && request.firstValidNumber(DocumentType.PASSPORT).isPresent();
    }

    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        PartnerARequest partnerRequest = new PartnerARequest(
                person.first(),
                person.last(),
                request.firstValidNumber(DocumentType.PASSPORT).orElseThrow(),
                person.ssn());
        return new PartnerResult("PARTNER_A", client.match(request.header().requestId(), partnerRequest).matches());
    }
}
