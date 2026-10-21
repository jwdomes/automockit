package com.example.router;

import com.example.flowdocs.Condition;
import com.example.flowdocs.FlowBranch;
import com.example.flowdocs.FlowStep;
import com.example.router.dto.RequestDto;
import com.example.router.dto.ResponseDto;
import com.example.router.idprovider.IdProviderService;
import com.example.router.partner.PartnerAService;
import com.example.router.partner.PartnerBService;
import com.example.router.partner.PartnerCService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Router {

    private final IdProviderService idProvider;
    private final boolean useNewIdProvider;
    private final PartnerAService partnerA;
    private final PartnerBService partnerB;
    private final PartnerCService partnerC;

    public Router(IdProviderService idProvider, @Value("${id-provider.use-new:false}") boolean useNewIdProvider,
                  PartnerAService partnerA, PartnerBService partnerB, PartnerCService partnerC) {
        this.idProvider = idProvider;
        this.useNewIdProvider = useNewIdProvider;
        this.partnerA = partnerA;
        this.partnerB = partnerB;
        this.partnerC = partnerC;
    }

    @FlowStep(action = Steps.DETERMINE_DOWNSTREAM_PARTNERS, participant = Participants.ROUTER)
    @FlowBranch(question = "Use new ID provider?", participant = Participants.ROUTER,
            gates = Steps.CALL_NEW_ID_PROVIDER, otherwise = Steps.CALL_LEGACY_ID_PROVIDER,
            conditions = @Condition(label = "new provider enabled", test = "#property('id-provider.use-new') == 'true'"))
    public void route(RequestDto request, ResponseDto response) {
        if (useNewIdProvider) {
            response.setPersonId(idProvider.lookupWithNew(request));
        } else {
            response.setPersonId(idProvider.lookupWithLegacy(request));
        }
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
