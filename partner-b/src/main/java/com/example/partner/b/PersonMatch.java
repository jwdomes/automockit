package com.example.partner.b;

import java.time.LocalDate;

public record PersonMatch(String firstName, String lastName, LocalDate birthDate, double matchProbability) {
}
