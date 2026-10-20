package com.example.router.partner;

import com.example.router.client.PartnerBClient;
import com.example.router.client.PartnerBRequest;
import com.example.router.dto.DocumentType;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.Person;
import com.example.router.dto.RequestDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PartnerBService {

    private final PartnerBClient client;

    public PartnerBService(PartnerBClient client) {
        this.client = client;
    }

    public boolean shouldCall(RequestDto request) {
        return StringUtils.hasText(request.person().pNumber())
                && request.firstValidNumber(DocumentType.DRIVER_LICENSE).isPresent();
    }

    public PartnerResult call(RequestDto request) {
        Person person = request.person();
        PartnerBRequest partnerRequest = new PartnerBRequest(
                person.first(),
                person.last(),
                person.birthDate(),
                request.firstValidNumber(DocumentType.DRIVER_LICENSE).orElseThrow(),
                person.pNumber());
        return new PartnerResult("PARTNER_B", client.match(request.header().requestId(), partnerRequest).matches());
    }
}
