package com.example.router.partner;

import com.example.router.client.PartnerAClient;
import com.example.router.client.PartnerARequest;
import com.example.router.docs.Decision;
import com.example.router.docs.FlowDecision;
import com.example.router.docs.FlowNotes;
import com.example.router.docs.Participant;
import com.example.router.dto.DocumentType;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.Person;
import com.example.router.dto.RequestDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PartnerAService {

    private final PartnerAClient client;
    private final FlowNotes notes;

    public PartnerAService(PartnerAClient client, FlowNotes notes) {
        this.client = client;
        this.notes = notes;
    }

    @FlowDecision(question = "Partner A eligible?", participant = Participant.ROUTER, gates = "Call Partner A")
    public Decision shouldCall(RequestDto request) {
        if (!StringUtils.hasText(request.person().ssn())) {
            return Decision.no("no SSN");
        }
        if (request.firstValidNumber(DocumentType.PASSPORT).isEmpty()) {
            return Decision.no("no valid passport");
        }
        return Decision.yes("SSN + valid passport");
    }

    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        String passport = request.firstValidNumber(DocumentType.PASSPORT).orElseThrow();
        notes.note("Using passport " + passport);
        PartnerARequest partnerRequest = new PartnerARequest(person.first(), person.last(), passport, person.ssn());
        return new PartnerResult("PARTNER_A", client.match(request.header().requestId(), partnerRequest).matches());
    }
}
