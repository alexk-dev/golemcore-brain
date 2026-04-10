package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.application.exception.WikiEditConflictException;
import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import dev.golemcore.brain.domain.WikiPage;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @ExceptionHandler(WikiNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(WikiNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(AuthUnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(AuthUnauthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(AuthAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AuthAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(WikiEditConflictException.class)
    public ResponseEntity<PageEditConflictResponse> handleEditConflict(WikiEditConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new PageEditConflictResponse(
                exception.getMessage(),
                "PAGE_EDIT_CONFLICT",
                exception.getExpectedRevision(),
                exception.getCurrentRevision(),
                toPage(exception)));
    }

    private WikiPage toPage(WikiEditConflictException exception) {
        return WikiPage.builder()
                .id(exception.getCurrentPage().getId())
                .path(exception.getCurrentPage().getPath())
                .parentPath(exception.getCurrentPage().getParentPath())
                .title(exception.getCurrentPage().getTitle())
                .slug(exception.getCurrentPage().getSlug())
                .kind(exception.getCurrentPage().getKind())
                .content(exception.getCurrentPage().getBody())
                .createdAt(DATE_TIME_FORMATTER.format(exception.getCurrentPage().getCreatedAt()))
                .updatedAt(DATE_TIME_FORMATTER.format(exception.getCurrentPage().getUpdatedAt()))
                .revision(exception.getCurrentPage().getRevision())
                .children(List.of())
                .build();
    }

    private record PageEditConflictResponse(
            String error,
            String code,
            String expectedRevision,
            String currentRevision,
            WikiPage currentPage) {
    }
}
