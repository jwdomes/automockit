package com.example.router;

import com.example.router.docs.FlowStep;
import com.example.router.docs.Participant;
import com.example.router.dto.RequestDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validates explicitly, rather than with @Valid, so validation is a step in the flow docs. */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    @FlowStep(action = "Validate request", participant = Participant.ROUTER)
    public void validate(RequestDto request) {
        Set<ConstraintViolation<RequestDto>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
