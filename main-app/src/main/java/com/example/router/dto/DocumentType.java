package com.example.router.dto;

import java.util.regex.Pattern;

public enum DocumentType {
    PASSPORT("[A-Z0-9]{9}"),
    DRIVER_LICENSE("[A-Z]\\d{8}"),
    NATIONAL_ID("\\d{10}");

    private final Pattern numberFormat;

    DocumentType(String numberFormat) {
        this.numberFormat = Pattern.compile(numberFormat);
    }

    public boolean isValid(String documentNumber) {
        return documentNumber != null && numberFormat.matcher(documentNumber).matches();
    }
}
