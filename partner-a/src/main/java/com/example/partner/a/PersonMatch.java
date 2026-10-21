package com.example.partner.a;

import java.time.LocalDate;

public record PersonMatch(String firstName, String lastName, LocalDate birthDate, double matchProbability) {
}
