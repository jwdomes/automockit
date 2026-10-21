package com.example.partner.b;

import java.util.Comparator;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class MatchService {

    // Ties broken by name so the same request always returns the same order.
    private static final Comparator<PersonMatch> RANKING = Comparator
            .comparingDouble(PersonMatch::matchProbability).reversed()
            .thenComparing(PersonMatch::lastName)
            .thenComparing(PersonMatch::firstName);

    private final IdentityRepository repository;

    public MatchService(IdentityRepository repository) {
        this.repository = repository;
    }

    public MatchResponse match(MatchRequest request) {
        return new MatchResponse(repository.findCandidates(request.pNumber(), request.documentNumber()).stream()
                .map(identity -> new PersonMatch(
                        identity.firstName(), identity.lastName(), identity.birthDate(), score(request, identity)))
                .sorted(RANKING)
                .toList());
    }

    private static double score(MatchRequest request, Identity identity) {
        double score = 0;
        if (Objects.equals(request.pNumber(), identity.pNumber())) {
            score += 0.3;
        }
        if (Objects.equals(request.documentNumber(), identity.documentNumber())) {
            score += 0.25;
        }
        if (Objects.equals(request.birthDate(), identity.birthDate())) {
            score += 0.2;
        }
        if (sameName(request.lastName(), identity.lastName())) {
            score += 0.15;
        }
        if (sameName(request.firstName(), identity.firstName())) {
            score += 0.1;
        }
        return Math.round(score * 100) / 100.0;
    }

    private static boolean sameName(String requested, String stored) {
        return requested != null && requested.equalsIgnoreCase(stored);
    }
}
