package com.example.partnerclients;

import java.time.LocalDate;

public record PartnerMatch(String firstName, String lastName, LocalDate birthDate, double matchProbability) {
}
