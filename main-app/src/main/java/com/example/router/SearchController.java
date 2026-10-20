package com.example.router;

import com.example.router.dto.RequestDto;
import com.example.router.dto.ResponseDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final Router router;

    public SearchController(Router router) {
        this.router = router;
    }

    @PostMapping("/search")
    public ResponseDto search(@Valid @RequestBody RequestDto request) {
        ResponseDto response = new ResponseDto(request.header().requestId());
        router.route(request, response);
        return response;
    }
}
