package dev.golemcore.brain.application.service.space;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.port.out.WikiRepository;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.space.Space;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SpaceService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,62}$");

    private final SpaceRepository spaceRepository;
    private final WikiRepository wikiRepository;

    public List<Space> listVisibleSpaces(AuthContext authContext) {
        List<Space> all = spaceRepository.listSpaces();
        if (authContext.isAuthDisabled() || authContext.isGlobalAdmin()) {
            return all;
        }
        return all.stream()
                .filter(space -> authContext.canAccessSpace(space.getId(), UserRole.VIEWER))
                .toList();
    }

    public Space getBySlug(String slug) {
        return spaceRepository.findBySlug(slug)
                .orElseThrow(() -> new WikiNotFoundException("Space not found: " + slug));
    }

    public Space requireAccess(AuthContext authContext, String slug, UserRole requiredRole) {
        Space space = getBySlug(slug);
        if (!authContext.canAccessSpace(space.getId(), requiredRole)) {
            throw new AuthAccessDeniedException("Access to space '" + slug + "' denied");
        }
        return space;
    }

    public Space createSpace(AuthContext authContext, String slug, String name) {
        requireGlobalAdmin(authContext);
        String normalizedSlug = normalizeSlug(slug);
        if (spaceRepository.findBySlug(normalizedSlug).isPresent()) {
            throw new IllegalArgumentException("Space with slug '" + normalizedSlug + "' already exists");
        }
        Space space = Space.builder()
                .id(UUID.randomUUID().toString())
                .slug(normalizedSlug)
                .name(name == null || name.isBlank() ? normalizedSlug : name.trim())
                .createdAt(Instant.now())
                .build();
        spaceRepository.save(space);
        wikiRepository.initializeSpace(space.getId());
        return space;
    }

    public void deleteSpace(AuthContext authContext, String slug) {
        requireGlobalAdmin(authContext);
        Space space = getBySlug(slug);
        spaceRepository.delete(space.getId());
        // Filesystem content is retained for safety; operators can purge manually.
    }

    private String normalizeSlug(String slug) {
        if (slug == null) {
            throw new IllegalArgumentException("Slug is required");
        }
        String normalized = slug.trim().toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid slug: must be lowercase alphanumeric with hyphens");
        }
        return normalized;
    }

    private void requireGlobalAdmin(AuthContext authContext) {
        if (!authContext.isGlobalAdmin()) {
            throw new AuthAccessDeniedException("Global admin required to manage spaces");
        }
    }
}
