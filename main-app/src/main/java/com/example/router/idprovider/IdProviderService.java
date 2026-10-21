package com.example.router.idprovider;

import com.example.flowdocs.FlowStep;
import com.example.partnerclients.IdLookupRequest;
import com.example.partnerclients.LegacyIdProviderClient;
import com.example.partnerclients.NewIdProviderClient;
import com.example.router.Participants;
import com.example.router.dto.Person;
import com.example.router.dto.RequestDto;
import org.springframework.stereotype.Service;

/** Resolves the request's person to a canonical person id with either ID provider. */
@Service
public class IdProviderService {

    private final NewIdProviderClient newProvider;
    private final LegacyIdProviderClient legacyProvider;

    public IdProviderService(NewIdProviderClient newProvider, LegacyIdProviderClient legacyProvider) {
        this.newProvider = newProvider;
        this.legacyProvider = legacyProvider;
    }

    @FlowStep(action = "Call New ID Provider", participant = Participants.NEW_ID_PROVIDER)
    public String lookupWithNew(RequestDto request) {
        return newProvider.lookup(request.header().requestId(), lookupRequest(request)).personId();
    }

    @FlowStep(action = "Call Legacy ID Provider", participant = Participants.LEGACY_ID_PROVIDER)
    public String lookupWithLegacy(RequestDto request) {
        return legacyProvider.lookup(request.header().requestId(), lookupRequest(request)).personId();
    }

    private static IdLookupRequest lookupRequest(RequestDto request) {
        Person person = request.person();
        return new IdLookupRequest(person.first(), person.last(), person.birthDate(), person.ssn(), person.pNumber());
    }
}
