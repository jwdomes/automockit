package com.example.partner.c;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/partner-c/matches")
    public MatchResponse match(@RequestBody MatchRequest request) {
        return matchService.match(request);
    }
}
