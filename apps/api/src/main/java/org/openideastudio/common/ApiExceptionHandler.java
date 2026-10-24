package org.openideastudio.common;

import java.util.Map;
import java.util.stream.Collectors;

import org.openideastudio.ai.InvalidModelOutputException;
import org.openideastudio.ai.ModelUnavailableException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps errors to RFC 9457 problem responses with a stable {@code code} property.
 * Messages are actionable without echoing project content back.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "stale-version", e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", e.getMessage());
    }

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail validationFailed(ValidationFailedException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, e.code(), e.getMessage());
        problem.setProperty("errors", e.errors());
        return problem;
    }

    @ExceptionHandler(ModelUnavailableException.class)
    ProblemDetail modelUnavailable(ModelUnavailableException e) {
        HttpStatus status = e.reason() == ModelUnavailableException.Reason.RATE_LIMITED
                ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
        return problem(status, e.reason().code(), e.getMessage());
    }

    @ExceptionHandler(InvalidModelOutputException.class)
    ProblemDetail invalidModelOutput(InvalidModelOutputException e) {
        return problem(HttpStatus.BAD_GATEWAY, "invalid-model-output",
                "The AI returned a response that could not be used. Try again, or rephrase your message.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request validation failed.");
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, f -> String.valueOf(f.getDefaultMessage()),
                        (first, second) -> first));
        problem.setProperty("fields", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
