package com.example.router.partner;

import static com.example.router.Requests.driverLicense;
import static com.example.router.Requests.passport;
import static com.example.router.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.router.client.PartnerAClient;
import com.example.router.client.PartnerARequest;
import com.example.router.client.PartnerMatchResponse;
import com.example.router.docs.Decision;
import com.example.router.docs.FlowNotes;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerAServiceTest {

    private final PartnerAClient client = mock(PartnerAClient.class);
    private final FlowNotes notes = mock(FlowNotes.class);
    private final PartnerAService service = new PartnerAService(client, notes);

    @Test
    void calledWithValidPassportAndSsn() {
        assertThat(service.shouldCall(request("111223333", null, passport("AB1234567"))))
                .isEqualTo(Decision.yes("SSN + valid passport"));
    }

    @Test
    void notCalledWithoutSsn() {
        assertThat(service.shouldCall(request(null, "PN1001", passport("AB1234567")))).isEqualTo(Decision.no("no SSN"));
        assertThat(service.shouldCall(request(" ", "PN1001", passport("AB1234567")))).isEqualTo(Decision.no("no SSN"));
    }

    @Test
    void notCalledWithInvalidPassportNumber() {
        assertThat(service.shouldCall(request("111223333", null, passport("AB12"))))
                .isEqualTo(Decision.no("no valid passport"));
    }

    @Test
    void notCalledWithOtherDocumentType() {
        assertThat(service.shouldCall(request("111223333", null, driverLicense("AB1234567"))))
                .isEqualTo(Decision.no("no valid passport"));
    }

    @Test
    void sendsFirstValidPassportAndSsnAsPersonId() {
        PartnerARequest expected = new PartnerARequest("Ada", "Lovelace", "AB1234567", "111223333");
        when(client.match(eq("req-1"), eq(expected))).thenReturn(new PartnerMatchResponse(List.of()));

        var result = service.call(request("111223333", null, passport("bad"), passport("AB1234567"), passport("ZZ9999999")));

        assertThat(result.partner()).isEqualTo("PARTNER_A");
        assertThat(result.matches()).isEmpty();
        verify(notes).note("Using passport AB1234567");
    }
}
