package com.example.router.partner;

import static com.example.router.Requests.nationalId;
import static com.example.router.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.router.client.PartnerCClient;
import com.example.router.client.PartnerCRequest;
import com.example.router.client.PartnerMatchResponse;
import com.example.router.docs.Decision;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerCServiceTest {

    private final PartnerCClient client = mock(PartnerCClient.class);
    private final PartnerCService service = new PartnerCService(client);

    @Test
    void calledWithSsnEvenWithoutDocuments() {
        assertThat(service.shouldCall(request("111223333", null))).isEqualTo(Decision.yes("SSN"));
    }

    @Test
    void notCalledWithOnlyPNumber() {
        assertThat(service.shouldCall(request(null, "PN1001", nationalId("1234567890")))).isEqualTo(Decision.no("no SSN"));
    }

    @Test
    void sendsSsnAsPersonIdentifier() {
        PartnerCRequest expected = new PartnerCRequest("Lovelace", LocalDate.of(1815, 12, 10), "111223333");
        when(client.match(eq("req-1"), eq(expected))).thenReturn(new PartnerMatchResponse(List.of()));

        assertThat(service.call(request("111223333", null)).partner()).isEqualTo("PARTNER_C");
    }
}
