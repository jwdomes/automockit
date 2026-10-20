package com.example.router.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record Person(
        @NotBlank String first,
        @NotBlank String last,
        String ssn,
        String pNumber,
        @NotNull LocalDate birthDate) {
}
