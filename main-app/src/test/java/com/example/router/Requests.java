package com.example.router;

import com.example.router.dto.Document;
import com.example.router.dto.DocumentType;
import com.example.router.dto.Header;
import com.example.router.dto.Person;
import com.example.router.dto.RequestDto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Requests {

    private Requests() {
    }

    public static RequestDto request(String ssn, String pNumber, Document... documents) {
        return new RequestDto(
                new Header("req-1", "test", Instant.parse("2026-10-20T12:00:00Z")),
                List.of(documents),
                new Person("Ada", "Lovelace", ssn, pNumber, LocalDate.of(1815, 12, 10)));
    }

    public static Document passport(String number) {
        return new Document(DocumentType.PASSPORT, number);
    }

    public static Document driverLicense(String number) {
        return new Document(DocumentType.DRIVER_LICENSE, number);
    }

    public static Document nationalId(String number) {
        return new Document(DocumentType.NATIONAL_ID, number);
    }
}
