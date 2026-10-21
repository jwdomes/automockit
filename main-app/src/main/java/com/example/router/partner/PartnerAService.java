package com.example.router.partner;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import com.example.partnerclients.PartnerAClient;
import com.example.partnerclients.PartnerARequest;
import com.example.router.Participants;
import com.example.router.dto.DocumentType;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.Person;
import com.example.router.dto.PersonMatch;
import com.example.router.dto.RequestDto;
import java.util.List;
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

    @FlowStep(action = "Call Partner A", participant = Participants.PARTNER_A,
            note = "Using passport #{#request.firstValidNumber(T(com.example.router.dto.DocumentType).PASSPORT).orElse('none')}")
    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        PartnerARequest partnerRequest = new PartnerARequest(
                person.first(),
                person.last(),
                request.firstValidNumber(DocumentType.PASSPORT).orElseThrow(),
                person.ssn());
        List<PersonMatch> matches = client.match(request.header().requestId(), partnerRequest).matches().stream()
                .map(PersonMatch::from)
                .toList();
        return new PartnerResult("PARTNER_A", matches);
    }
}
