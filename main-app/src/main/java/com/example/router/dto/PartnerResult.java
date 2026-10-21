package com.example.router.dto;

import java.util.List;

public record PartnerResult(String partner, List<PersonMatch> matches) {
}
