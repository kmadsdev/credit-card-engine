package io.github.kmadsdev.cardengine.api;

import io.github.kmadsdev.cardengine.application.ConcurrentUpdateException;
import io.github.kmadsdev.cardengine.application.CooldownActiveException;
import io.github.kmadsdev.cardengine.application.IdempotencyKeyReuseException;
import io.github.kmadsdev.cardengine.application.NotFoundException;
import io.github.kmadsdev.cardengine.domain.DomainException;
import io.github.kmadsdev.cardengine.domain.IllegalStateTransitionException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.data.mapping.PropertyReferenceException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Every error is an RFC 7807 problem ({@code application/problem+json}). The
 * {@code type} is a stable URN with a short code, so clients can branch on it.
 * Messages never contain a CPF or a card number.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    static final String TYPE_PREFIX = "urn:card-engine:problem:";

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create(TYPE_PREFIX + code));
        p.setTitle(status.getReasonPhrase());
        return p;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", e.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ProblemDetail illegalTransition(IllegalStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.code(), e.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    ProblemDetail idempotencyReuse(IdempotencyKeyReuseException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency-key-reused", e.getMessage());
    }

    @ExceptionHandler(ConcurrentUpdateException.class)
    ProblemDetail concurrent(ConcurrentUpdateException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", e.getMessage());
    }

    @ExceptionHandler(CooldownActiveException.class)
    ResponseEntity<ProblemDetail> cooldown(CooldownActiveException e) {
        ProblemDetail p = problem(HttpStatus.TOO_MANY_REQUESTS, "upgrade-cooldown", e.getMessage());
        p.setProperty("retryAt", e.retryAt().toString());
        long seconds = Math.max(1, Duration.between(clock.instant(), e.retryAt()).toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds)).body(p);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail badSort(PropertyReferenceException e) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-sort", "Unknown sort property: " + e.getPropertyName());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "validation", "Request body is not valid");
        p.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getAllErrors().stream()
                .map(e -> Map.of("field", fieldOf(e), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "validation", "Request is not valid");
        p.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(p);
    }

    private static String fieldOf(MessageSourceResolvable error) {
        if (error instanceof FieldError f) {
            return f.getField();
        }
        String[] codes = error.getCodes();
        return codes != null && codes.length > 0 ? codes[codes.length - 1] : "request";
    }
}
