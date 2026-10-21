package com.example.partner.b;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchServiceTest {

    private static final LocalDate DOB = LocalDate.of(1815, 12, 10);

    private final IdentityRepository repository = mock(IdentityRepository.class);
    private final MatchService service = new MatchService(repository);

    @Test
    void ranksCandidatesByWeightedFieldMatches() {
        when(repository.findCandidates("PN1001", "L10000001")).thenReturn(List.of(
                new Identity("Ada", "King", LocalDate.of(1816, 1, 1), "PN1001", "K99999999"),
                new Identity("Ada", "Lovelace", DOB, "PN9001", "L10000001"),
                new Identity("Ada", "Lovelace", DOB, "PN1001", "L10000001")));

        List<PersonMatch> matches = service.match(
                new MatchRequest("Ada", "lovelace", DOB, "L10000001", "PN1001")).matches();

        assertThat(matches).extracting(PersonMatch::lastName, PersonMatch::matchProbability)
                .containsExactly(tuple("Lovelace", 1.0), tuple("Lovelace", 0.7), tuple("King", 0.4));
    }

    @Test
    void noCandidatesMeansEmptyList() {
        when(repository.findCandidates("PN0000", "Z00000000")).thenReturn(List.of());

        assertThat(service.match(new MatchRequest("No", "One", DOB, "Z00000000", "PN0000")).matches()).isEmpty();
    }
}
