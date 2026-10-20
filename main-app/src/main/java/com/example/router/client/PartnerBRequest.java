package com.example.router.client;

import java.time.LocalDate;

public record PartnerBRequest(
        String firstName, String lastName, LocalDate birthDate, String documentNumber, String pNumber) {
}
