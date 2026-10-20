package com.example.partner.a;

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
        when(repository.findCandidates("111223333", "AB1234567")).thenReturn(List.of(
                new Identity("Augusta", "King", DOB, "999887777", "AB1234567"),
                new Identity("Ada", "Lovelace", DOB, "111223333", "AB1234567"),
                new Identity("Ada", "Byron", DOB, "111223333", "XY0000001")));

        List<PersonMatch> matches = service.match(
                new MatchRequest("ada", "LOVELACE", "AB1234567", "111223333")).matches();

        assertThat(matches).extracting(PersonMatch::lastName, PersonMatch::matchProbability)
                .containsExactly(
                        tuple("Lovelace", 1.0),
                        tuple("Byron", 0.55),
                        tuple("King", 0.3));
    }

    @Test
    void noCandidatesMeansEmptyList() {
        when(repository.findCandidates("000000000", "ZZ0000000")).thenReturn(List.of());

        assertThat(service.match(new MatchRequest("No", "One", "ZZ0000000", "000000000")).matches()).isEmpty();
    }
}
