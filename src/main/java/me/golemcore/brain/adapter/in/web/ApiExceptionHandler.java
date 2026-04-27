/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import me.golemcore.brain.application.exception.WikiEditConflictException;
import me.golemcore.brain.application.exception.WikiNotFoundException;
import me.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import me.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import me.golemcore.brain.application.service.auth.LoginThrottledException;
import me.golemcore.brain.domain.WikiPage;
import me.golemcore.brain.web.RequestIdFilter;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @ExceptionHandler(WikiNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(WikiNotFoundException exception, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(exception.getMessage(), req));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException exception,
            HttpServletRequest req) {
        return ResponseEntity.badRequest().body(body(exception.getMessage(), req));
    }

    @ExceptionHandler(AuthUnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(AuthUnauthorizedException exception,
            HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body(exception.getMessage(), req));
    }

    @ExceptionHandler(AuthAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AuthAccessDeniedException exception,
            HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(exception.getMessage(), req));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest req) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.badRequest().body(body(message, req));
    }

    @ExceptionHandler(LoginThrottledException.class)
    public ResponseEntity<Map<String, Object>> handleLoginThrottled(LoginThrottledException exception,
            HttpServletRequest req) {
        long retryAfter = Math.max(1L, exception.getRetryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(retryAfter))
                .body(body(exception.getMessage(), req));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException exception,
            HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String reason = exception.getReason() != null ? exception.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status).body(body(reason, req));
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

    /**
     * Catch-all for unexpected exceptions: logs the full stack trace with the
     * request id and returns a generic message to the client so internal details
     * (paths, SQL, JPA messages) do not leak. The client can correlate via the
     * {@code X-Request-Id} header. Standard Spring web errors (unknown route,
     * method not allowed, etc.) implement {@link ErrorResponse} and are passed
     * through with their original status code.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception, HttpServletRequest req) {
        if (exception instanceof ErrorResponse errorResponse) {
            // Keep our {error,requestId} body shape so the frontend's existing error reader
            // keeps
            // working, but extract a useful message from the ProblemDetail (detail > title
            // >
            // status reason phrase) so 404/405/415 etc. surface meaningfully in the UI.
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            ProblemDetail problem = errorResponse.getBody();
            String message = problem != null && problem.getDetail() != null ? problem.getDetail()
                    : problem != null && problem.getTitle() != null ? problem.getTitle()
                            : status.getReasonPhrase();
            return ResponseEntity.status(status)
                    .headers(errorResponse.getHeaders())
                    .body(body(message, req));
        }
        String requestId = (String) req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        log.error("Unhandled exception (requestId={})", requestId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("Internal server error", req));
    }

    private static Map<String, Object> body(String error, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        Object requestId = req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId != null) {
            body.put("requestId", requestId);
        }
        return body;
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
