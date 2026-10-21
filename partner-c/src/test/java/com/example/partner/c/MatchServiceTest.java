package com.example.partner.c;

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
    void ranksCandidatesByWeightedFieldMatchesWithNameTieBreak() {
        when(repository.findCandidates("111223333")).thenReturn(List.of(
                new Identity("Ada", "Lovelace", DOB.plusDays(1), "111223333"),
                new Identity("Ada", "Byron", DOB, "111223333"),
                new Identity("Ada", "Lovelace", DOB, "111223333")));

        List<PersonMatch> matches = service.match(new MatchRequest("LOVELACE", DOB, "111223333")).matches();

        assertThat(matches).extracting(PersonMatch::lastName, PersonMatch::matchProbability)
                .containsExactly(tuple("Lovelace", 1.0), tuple("Byron", 0.75), tuple("Lovelace", 0.75));
    }

    @Test
    void noCandidatesMeansEmptyList() {
        when(repository.findCandidates("000000000")).thenReturn(List.of());

        assertThat(service.match(new MatchRequest("One", DOB, "000000000")).matches()).isEmpty();
    }
}
