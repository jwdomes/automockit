package com.example.router.partner;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowDecision;
import com.example.flowdocs.FlowStep;
import com.example.partnerclients.PartnerCClient;
import com.example.partnerclients.PartnerCRequest;
import com.example.router.Participants;
import com.example.router.Steps;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.Person;
import com.example.router.dto.PersonMatch;
import com.example.router.dto.RequestDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PartnerCService {

    private final PartnerCClient client;

    public PartnerCService(PartnerCClient client) {
        this.client = client;
    }

    @FlowDecision(question = "Partner C eligible?", participant = Participants.ROUTER, gates = Steps.CALL_PARTNER_C,
            conditions = @Condition(label = "SSN present", test = "#hasText(#request.person().ssn())"))
    public boolean shouldCall(RequestDto request) {
        return StringUtils.hasText(request.person().ssn());
    }

    @FlowStep(action = Steps.CALL_PARTNER_C, participant = Participants.PARTNER_C)
    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        PartnerCRequest partnerRequest = new PartnerCRequest(person.last(), person.birthDate(), person.ssn());
        List<PersonMatch> matches = client.match(request.header().requestId(), partnerRequest).matches().stream()
                .map(PersonMatch::from)
                .toList();
        return new PartnerResult("PARTNER_C", matches);
    }
}
