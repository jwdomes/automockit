package com.example.partner.a;

import java.time.LocalDate;

public record Identity(String firstName, String lastName, LocalDate birthDate, String ssn, String documentNumber) {
}
