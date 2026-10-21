package com.example.partnerclients;

import java.time.LocalDate;

public record PartnerCRequest(String lastName, LocalDate birthDate, String personIdentifier) {
}
