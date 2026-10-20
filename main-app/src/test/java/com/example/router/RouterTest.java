package com.example.router;

import static com.example.router.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.router.docs.Decision;
import com.example.router.dto.PartnerResult;
import com.example.router.dto.RequestDto;
import com.example.router.dto.ResponseDto;
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
    private final Router router = new Router(partnerA, partnerB, partnerC);
    private final RequestDto request = request("111223333", "PN1001");

    @Test
    void callsEligiblePartnersInOrderAndAggregatesResults() {
        PartnerResult a = new PartnerResult("PARTNER_A", List.of());
        PartnerResult b = new PartnerResult("PARTNER_B", List.of());
        PartnerResult c = new PartnerResult("PARTNER_C", List.of());
        when(partnerA.shouldCall(request)).thenReturn(Decision.yes("a"));
        when(partnerB.shouldCall(request)).thenReturn(Decision.yes("b"));
        when(partnerC.shouldCall(request)).thenReturn(Decision.yes("c"));
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
        when(partnerA.shouldCall(request)).thenReturn(Decision.no("a"));
        when(partnerB.shouldCall(request)).thenReturn(Decision.no("b"));
        when(partnerC.shouldCall(request)).thenReturn(Decision.yes("c"));
        when(partnerC.call(request)).thenReturn(c);
        ResponseDto response = new ResponseDto("req-1");

        router.route(request, response);

        verify(partnerA, never()).call(any());
        verify(partnerB, never()).call(any());
        assertThat(response.getResults()).containsExactly(c);
    }
}
