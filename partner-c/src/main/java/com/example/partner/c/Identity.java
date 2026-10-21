package com.example.partner.c;

import java.time.LocalDate;

public record Identity(String firstName, String lastName, LocalDate birthDate, String ssn) {
}
