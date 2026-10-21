package com.example.partner.b;

import java.time.LocalDate;

public record MatchRequest(
        String firstName, String lastName, LocalDate birthDate, String documentNumber, String pNumber) {
}
