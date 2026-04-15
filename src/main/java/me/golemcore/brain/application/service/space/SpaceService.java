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

package me.golemcore.brain.application.service.space;

import me.golemcore.brain.application.exception.WikiNotFoundException;
import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.application.port.out.WikiRepository;
import me.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import me.golemcore.brain.domain.auth.AuthContext;
import me.golemcore.brain.domain.auth.UserRole;
import me.golemcore.brain.domain.space.Space;
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
