package com.example.router.partner;

import static com.example.router.Requests.driverLicense;
import static com.example.router.Requests.passport;
import static com.example.router.Requests.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.router.client.PartnerBClient;
import com.example.router.client.PartnerBRequest;
import com.example.router.client.PartnerMatchResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartnerBServiceTest {

    private final PartnerBClient client = mock(PartnerBClient.class);
    private final PartnerBService service = new PartnerBService(client);

    @Test
    void calledWithValidDriverLicenseAndPNumber() {
        assertThat(service.shouldCall(request(null, "PN1001", driverLicense("L10000001")))).isTrue();
    }

    @Test
    void notCalledWithoutPNumber() {
        assertThat(service.shouldCall(request("111223333", null, driverLicense("L10000001")))).isFalse();
    }

    @Test
    void notCalledWithInvalidDriverLicenseNumber() {
        assertThat(service.shouldCall(request(null, "PN1001", driverLicense("10000001")))).isFalse();
    }

    @Test
    void notCalledWithOtherDocumentType() {
        assertThat(service.shouldCall(request(null, "PN1001", passport("L10000001")))).isFalse();
    }

    @Test
    void sendsBirthDateDriverLicenseAndPNumber() {
        PartnerBRequest expected = new PartnerBRequest(
                "Ada", "Lovelace", LocalDate.of(1815, 12, 10), "L10000001", "PN1001");
        when(client.match(eq("req-1"), eq(expected))).thenReturn(new PartnerMatchResponse(List.of()));

        assertThat(service.call(request(null, "PN1001", driverLicense("L10000001"))).partner()).isEqualTo("PARTNER_B");
    }
}
