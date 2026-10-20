package com.example.router.client;

import java.time.LocalDate;

public record PartnerCRequest(String lastName, LocalDate birthDate, String personIdentifier) {
}
