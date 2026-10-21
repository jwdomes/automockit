package com.example.partnerclients;

import java.time.LocalDate;

/** Sent to either ID provider; ssn and pNumber may be null. */
public record IdLookupRequest(String firstName, String lastName, LocalDate birthDate, String ssn, String pNumber) {
}
