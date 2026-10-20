package com.example.router.partner;

import com.example.router.client.PartnerBClient;
import com.example.router.client.PartnerBRequest;
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
public class PartnerBService {

    private final PartnerBClient client;
    private final FlowNotes notes;

    public PartnerBService(PartnerBClient client, FlowNotes notes) {
        this.client = client;
        this.notes = notes;
    }

    @FlowDecision(question = "Partner B eligible?", participant = Participant.ROUTER, gates = "Call Partner B")
    public Decision shouldCall(RequestDto request) {
        if (!StringUtils.hasText(request.person().pNumber())) {
            return Decision.no("no PNumber");
        }
        if (request.firstValidNumber(DocumentType.DRIVER_LICENSE).isEmpty()) {
            return Decision.no("no valid driver license");
        }
        return Decision.yes("PNumber + valid driver license");
    }

    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        String driverLicense = request.firstValidNumber(DocumentType.DRIVER_LICENSE).orElseThrow();
        notes.note("Using driver license " + driverLicense);
        PartnerBRequest partnerRequest = new PartnerBRequest(
                person.first(), person.last(), person.birthDate(), driverLicense, person.pNumber());
        return new PartnerResult("PARTNER_B", client.match(request.header().requestId(), partnerRequest).matches());
    }
}
