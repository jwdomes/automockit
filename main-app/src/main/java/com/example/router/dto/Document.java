package com.example.router.dto;

import jakarta.validation.constraints.NotNull;

public record Document(@NotNull DocumentType documentType, String documentNumber) {
}
