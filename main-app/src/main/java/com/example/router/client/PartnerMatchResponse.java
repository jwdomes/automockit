package com.example.router.client;

import com.example.router.dto.PersonMatch;
import java.util.List;

public record PartnerMatchResponse(List<PersonMatch> matches) {
}
