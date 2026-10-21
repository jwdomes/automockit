package com.example.router.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentTypeTest {

    @Test
    void passportIsNineUppercaseAlphanumerics() {
        assertThat(DocumentType.PASSPORT.isValid("AB1234567")).isTrue();
        assertThat(DocumentType.PASSPORT.isValid("AB123456")).isFalse();
        assertThat(DocumentType.PASSPORT.isValid("ab1234567")).isFalse();
    }

    @Test
    void driverLicenseIsLetterThenEightDigits() {
        assertThat(DocumentType.DRIVER_LICENSE.isValid("L10000001")).isTrue();
        assertThat(DocumentType.DRIVER_LICENSE.isValid("110000001")).isFalse();
        assertThat(DocumentType.DRIVER_LICENSE.isValid("L1000000")).isFalse();
    }

    @Test
    void nationalIdIsTenDigits() {
        assertThat(DocumentType.NATIONAL_ID.isValid("1234567890")).isTrue();
        assertThat(DocumentType.NATIONAL_ID.isValid("123456789")).isFalse();
    }

    @Test
    void nullIsNeverValid() {
        assertThat(DocumentType.PASSPORT.isValid(null)).isFalse();
    }
}
