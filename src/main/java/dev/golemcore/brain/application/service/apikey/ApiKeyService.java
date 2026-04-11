package dev.golemcore.brain.application.service.apikey;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.ApiKeyRepository;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.application.service.auth.JwtService;
import dev.golemcore.brain.domain.apikey.ApiKey;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.space.Space;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SpaceRepository spaceRepository;
    private final JwtService jwtService;

    public IssuedApiKey issueGlobal(AuthContext authContext, String name, Set<UserRole> roles, Instant expiresAt) {
        requireGlobalAdmin(authContext);
        return issue(authContext, name, null, normalizeRoles(roles), expiresAt);
    }

    public IssuedApiKey issueForSpace(AuthContext authContext, String spaceSlug, String name, Set<UserRole> roles, Instant expiresAt) {
        Space space = spaceRepository.findBySlug(spaceSlug)
                .orElseThrow(() -> new WikiNotFoundException("Space not found: " + spaceSlug));
        if (!authContext.canAccessSpace(space.getId(), UserRole.ADMIN)) {
            throw new AuthAccessDeniedException("Admin access to space '" + spaceSlug + "' required");
        }
        return issue(authContext, name, space.getId(), normalizeRoles(roles), expiresAt);
    }

    public List<ApiKey> listGlobal(AuthContext authContext) {
        requireGlobalAdmin(authContext);
        return apiKeyRepository.listBySpace(null);
    }

    public List<ApiKey> listForSpace(AuthContext authContext, String spaceSlug) {
        Space space = spaceRepository.findBySlug(spaceSlug)
                .orElseThrow(() -> new WikiNotFoundException("Space not found: " + spaceSlug));
        if (!authContext.canAccessSpace(space.getId(), UserRole.ADMIN)) {
            throw new AuthAccessDeniedException("Admin access to space '" + spaceSlug + "' required");
        }
        return apiKeyRepository.listBySpace(space.getId());
    }

    public void revoke(AuthContext authContext, String keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new WikiNotFoundException("API key not found: " + keyId));
        if (key.isGlobal()) {
            requireGlobalAdmin(authContext);
        } else if (!authContext.canAccessSpace(key.getSpaceId(), UserRole.ADMIN)) {
            throw new AuthAccessDeniedException("Admin access required to revoke this key");
        }
        apiKeyRepository.save(ApiKey.builder()
                .id(key.getId())
                .name(key.getName())
                .subject(key.getSubject())
                .spaceId(key.getSpaceId())
                .roles(key.getRoles())
                .createdAt(key.getCreatedAt())
                .expiresAt(key.getExpiresAt())
                .revoked(true)
                .build());
    }

    public ApiKey findActive(String jti) {
        ApiKey apiKey = apiKeyRepository.findById(jti).orElse(null);
        if (apiKey == null || apiKey.isRevoked()) {
            return null;
        }
        if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        return apiKey;
    }

    private IssuedApiKey issue(AuthContext authContext, String name, String spaceId, Set<UserRole> roles, Instant expiresAt) {
        String subject = authContext.getUser() != null
                ? "user:" + authContext.getUser().getId()
                : "service:anonymous";
        ApiKey apiKey = ApiKey.builder()
                .id(UUID.randomUUID().toString())
                .name(Objects.requireNonNullElse(name, "unnamed"))
                .subject(subject)
                .spaceId(spaceId)
                .roles(roles)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
        apiKeyRepository.save(apiKey);
        String token = jwtService.issue(apiKey);
        return new IssuedApiKey(apiKey, token);
    }

    private Set<UserRole> normalizeRoles(Set<UserRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return EnumSet.of(UserRole.VIEWER);
        }
        return EnumSet.copyOf(roles);
    }

    private void requireGlobalAdmin(AuthContext authContext) {
        if (!authContext.isGlobalAdmin()) {
            throw new AuthAccessDeniedException("Global admin required");
        }
    }

    public record IssuedApiKey(ApiKey apiKey, String token) {}
}
