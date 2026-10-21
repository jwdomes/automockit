package com.example.router.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record Header(@NotBlank String requestId, String source, Instant timestamp) {
}
