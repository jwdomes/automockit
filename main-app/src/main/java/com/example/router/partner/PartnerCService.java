package com.example.router.partner;

import com.example.router.client.PartnerCClient;
import com.example.router.client.PartnerCRequest;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.Person;
import com.example.router.dto.RequestDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PartnerCService {

    private final PartnerCClient client;

    public PartnerCService(PartnerCClient client) {
        this.client = client;
    }

    public boolean shouldCall(RequestDto request) {
        return StringUtils.hasText(request.person().ssn());
    }

    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        PartnerCRequest partnerRequest = new PartnerCRequest(person.last(), person.birthDate(), person.ssn());
        return new PartnerResult("PARTNER_C", client.match(request.header().requestId(), partnerRequest).matches());
    }
}
