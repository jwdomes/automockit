package com.example.router;

import static com.example.router.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.router.dto.PartnerResult;
import com.example.router.dto.RequestDto;
import com.example.router.dto.ResponseDto;
import com.example.router.idprovider.IdProviderService;
import com.example.router.partner.PartnerAService;
import com.example.router.partner.PartnerBService;
import com.example.router.partner.PartnerCService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class RouterTest {

    private final PartnerAService partnerA = mock(PartnerAService.class);
    private final PartnerBService partnerB = mock(PartnerBService.class);
    private final PartnerCService partnerC = mock(PartnerCService.class);
    private final IdProviderService idProvider = mock(IdProviderService.class);
    private final Router router = new Router(idProvider, false, partnerA, partnerB, partnerC);
    private final RequestDto request = request("111223333", "PN1001");

    @Test
    void callsEligiblePartnersInOrderAndAggregatesResults() {
        PartnerResult a = new PartnerResult("PARTNER_A", List.of());
        PartnerResult b = new PartnerResult("PARTNER_B", List.of());
        PartnerResult c = new PartnerResult("PARTNER_C", List.of());
        when(partnerA.shouldCall(request)).thenReturn(true);
        when(partnerB.shouldCall(request)).thenReturn(true);
        when(partnerC.shouldCall(request)).thenReturn(true);
        when(partnerA.call(request)).thenReturn(a);
        when(partnerB.call(request)).thenReturn(b);
        when(partnerC.call(request)).thenReturn(c);
        ResponseDto response = new ResponseDto("req-1");

        router.route(request, response);

        InOrder order = inOrder(partnerA, partnerB, partnerC);
        order.verify(partnerA).call(request);
        order.verify(partnerB).call(request);
        order.verify(partnerC).call(request);
        assertThat(response.getResults()).containsExactly(a, b, c);
    }

    @Test
    void skippedPartnersAreLeftOutOfResults() {
        PartnerResult c = new PartnerResult("PARTNER_C", List.of());
        when(partnerC.shouldCall(request)).thenReturn(true);
        when(partnerC.call(request)).thenReturn(c);
        ResponseDto response = new ResponseDto("req-1");

        router.route(request, response);

        verify(partnerA, never()).call(any());
        verify(partnerB, never()).call(any());
        assertThat(response.getResults()).containsExactly(c);
    }

    @Test
    void resolvesThePersonIdWithTheLegacyProviderByDefault() {
        when(idProvider.lookupWithLegacy(request)).thenReturn("LEGACY-1");
        ResponseDto response = new ResponseDto("req-1");

        router.route(request, response);

        assertThat(response.getPersonId()).isEqualTo("LEGACY-1");
        verify(idProvider, never()).lookupWithNew(any());
    }

    @Test
    void resolvesThePersonIdWithTheNewProviderWhenSwitchedOn() {
        Router switched = new Router(idProvider, true, partnerA, partnerB, partnerC);
        when(idProvider.lookupWithNew(request)).thenReturn("NEW-1");
        ResponseDto response = new ResponseDto("req-1");

        switched.route(request, response);

        assertThat(response.getPersonId()).isEqualTo("NEW-1");
        verify(idProvider, never()).lookupWithLegacy(any());
    }
}
