package com.example.router.dto;

import com.example.partnerclients.PartnerMatch;
import java.time.LocalDate;

public record PersonMatch(String firstName, String lastName, LocalDate birthDate, double matchProbability) {

    public static PersonMatch from(PartnerMatch match) {
        return new PersonMatch(match.firstName(), match.lastName(), match.birthDate(), match.matchProbability());
    }
}
