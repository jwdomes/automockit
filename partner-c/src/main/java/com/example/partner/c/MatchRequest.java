package com.example.partner.c;

import java.time.LocalDate;

public record MatchRequest(String lastName, LocalDate birthDate, String personIdentifier) {
}
