package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.adapter.in.web.auth.AuthContextResolver;
import dev.golemcore.brain.application.service.apikey.ApiKeyService;
import dev.golemcore.brain.application.service.apikey.ApiKeyService.IssuedApiKey;
import dev.golemcore.brain.domain.apikey.ApiKey;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuthContextResolver authContextResolver;

    @GetMapping("/api/api-keys")
    public List<ApiKey> listGlobalKeys(HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        return apiKeyService.listGlobal(context);
    }

    @PostMapping("/api/api-keys")
    public ResponseEntity<IssuedApiKeyResponse> createGlobalKey(
            @Valid @RequestBody CreateApiKeyRequest payload, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        IssuedApiKey issued = apiKeyService.issueGlobal(
                context, payload.getName(), resolveRoles(payload.getRoles()), resolveExpiresAt(payload.getExpiresAt()));
        return ResponseEntity.status(201).body(IssuedApiKeyResponse.of(issued));
    }

    @GetMapping("/api/spaces/{slug}/api-keys")
    public List<ApiKey> listSpaceKeys(@PathVariable String slug, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        return apiKeyService.listForSpace(context, slug);
    }

    @PostMapping("/api/spaces/{slug}/api-keys")
    public ResponseEntity<IssuedApiKeyResponse> createSpaceKey(
            @PathVariable String slug,
            @Valid @RequestBody CreateApiKeyRequest payload,
            HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        IssuedApiKey issued = apiKeyService.issueForSpace(
                context, slug, payload.getName(), resolveRoles(payload.getRoles()), resolveExpiresAt(payload.getExpiresAt()));
        return ResponseEntity.status(201).body(IssuedApiKeyResponse.of(issued));
    }

    @DeleteMapping("/api/api-keys/{keyId}")
    public ResponseEntity<Void> revoke(@PathVariable String keyId, HttpServletRequest request) {
        AuthContext context = authContextResolver.requireAuthenticated(request);
        apiKeyService.revoke(context, keyId);
        return ResponseEntity.noContent().build();
    }

    private Set<UserRole> resolveRoles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return EnumSet.of(UserRole.VIEWER);
        }
        return EnumSet.copyOf(roles);
    }

    private Instant resolveExpiresAt(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) {
            return null;
        }
        return Instant.parse(expiresAt);
    }

    @Data
    public static class CreateApiKeyRequest {
        @NotBlank
        @Size(max = 120)
        private String name;

        private Set<UserRole> roles;

        /** ISO-8601 instant, or null for non-expiring key. */
        private String expiresAt;
    }

    @Value
    public static class IssuedApiKeyResponse {
        ApiKey apiKey;
        String token;

        public static IssuedApiKeyResponse of(IssuedApiKey issued) {
            return new IssuedApiKeyResponse(issued.apiKey(), issued.token());
        }
    }
}
