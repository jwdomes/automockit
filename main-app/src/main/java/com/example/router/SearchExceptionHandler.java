package com.example.router;

import com.example.router.docs.FlowStep;
import com.example.router.docs.Participant;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class SearchExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SearchExceptionHandler.class);

    @ExceptionHandler(RestClientException.class)
    @FlowStep(action = "Map partner failure to 502", participant = Participant.ROUTER)
    public ProblemDetail partnerCallFailed(RestClientException e) {
        log.warn("Partner call failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, "Partner call failed");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @FlowStep(action = "Map invalid request to 400", participant = Participant.ROUTER)
    public ProblemDetail invalidRequest(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
