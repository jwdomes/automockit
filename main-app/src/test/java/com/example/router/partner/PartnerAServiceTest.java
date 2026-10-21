package com.example.router.partner;

import static com.example.router.Requests.driverLicense;
import static com.example.router.Requests.passport;
import static com.example.router.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.partnerclients.PartnerAClient;
import com.example.partnerclients.PartnerARequest;
import com.example.partnerclients.PartnerMatchResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerAServiceTest {

    private final PartnerAClient client = mock(PartnerAClient.class);
    private final PartnerAService service = new PartnerAService(client);

    @Test
    void calledWithValidPassportAndSsn() {
        assertThat(service.shouldCall(request("111223333", null, passport("AB1234567")))).isTrue();
    }

    @Test
    void notCalledWithoutSsn() {
        assertThat(service.shouldCall(request(null, "PN1001", passport("AB1234567")))).isFalse();
        assertThat(service.shouldCall(request(" ", "PN1001", passport("AB1234567")))).isFalse();
    }

    @Test
    void notCalledWithInvalidPassportNumber() {
        assertThat(service.shouldCall(request("111223333", null, passport("AB12")))).isFalse();
    }

    @Test
    void notCalledWithOtherDocumentType() {
        assertThat(service.shouldCall(request("111223333", null, driverLicense("AB1234567")))).isFalse();
    }

    @Test
    void sendsFirstValidPassportAndSsnAsPersonId() {
        PartnerARequest expected = new PartnerARequest("Ada", "Lovelace", "AB1234567", "111223333");
        when(client.match(eq("req-1"), eq(expected))).thenReturn(new PartnerMatchResponse(List.of()));

        var result = service.call(request("111223333", null, passport("bad"), passport("AB1234567"), passport("ZZ9999999")));

        assertThat(result.partner()).isEqualTo("PARTNER_A");
        assertThat(result.matches()).isEmpty();
    }
}
