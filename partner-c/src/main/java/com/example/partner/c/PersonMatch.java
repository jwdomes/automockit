package com.example.partner.c;

import java.time.LocalDate;

public record PersonMatch(String firstName, String lastName, LocalDate birthDate, double matchProbability) {
}
