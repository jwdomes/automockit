package com.example.router.dto;

import java.util.ArrayList;
import java.util.List;

public class ResponseDto {

    private final String requestId;
    private final List<PartnerResult> results = new ArrayList<>();

    public ResponseDto(String requestId) {
        this.requestId = requestId;
    }

    public void add(PartnerResult result) {
        results.add(result);
    }

    public String getRequestId() {
        return requestId;
    }

    public List<PartnerResult> getResults() {
        return List.copyOf(results);
    }
}
