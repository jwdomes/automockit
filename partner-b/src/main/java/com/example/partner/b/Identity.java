package com.example.partner.b;

import java.time.LocalDate;

public record Identity(
        String firstName, String lastName, LocalDate birthDate, String pNumber, String documentNumber) {
}
