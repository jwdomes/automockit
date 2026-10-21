package com.example.router;

import com.example.flowdocs.FlowStep;
import com.example.router.dto.RequestDto;
import com.example.router.dto.ResponseDto;
import com.example.router.partner.PartnerAService;
import com.example.router.partner.PartnerBService;
import com.example.router.partner.PartnerCService;
import org.springframework.stereotype.Component;

@Component
public class Router {

    private final PartnerAService partnerA;
    private final PartnerBService partnerB;
    private final PartnerCService partnerC;

    public Router(PartnerAService partnerA, PartnerBService partnerB, PartnerCService partnerC) {
        this.partnerA = partnerA;
        this.partnerB = partnerB;
        this.partnerC = partnerC;
    }

    @FlowStep(action = "Determine downstream partners", participant = Participants.ROUTER)
    public void route(RequestDto request, ResponseDto response) {
        if (partnerA.shouldCall(request)) {
            response.add(partnerA.call(request));
        }
        if (partnerB.shouldCall(request)) {
            response.add(partnerB.call(request));
        }
        if (partnerC.shouldCall(request)) {
            response.add(partnerC.call(request));
        }
    }
}
