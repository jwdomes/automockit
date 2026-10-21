package com.example.router.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

public record RequestDto(
        @NotNull @Valid Header header,
        List<@Valid Document> documents,
        @NotNull @Valid Person person) {

    public RequestDto {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    public Optional<String> firstValidNumber(DocumentType type) {
        return documents.stream()
                .filter(document -> document.documentType() == type && type.isValid(document.documentNumber()))
                .map(Document::documentNumber)
                .findFirst();
    }
}
