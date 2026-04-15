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

package me.golemcore.brain.web;

import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.application.space.SpaceContextHolder;
import me.golemcore.brain.domain.space.Space;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class SpaceResolverFilter extends OncePerRequestFilter {

    public static final String SPACE_SLUG_ATTRIBUTE = "brain.spaceSlug";
    public static final String SPACE_ID_ATTRIBUTE = "brain.spaceId";

    private static final Pattern SPACE_PATH = Pattern.compile("^/api/spaces/([^/]+)(?:/.*)?$");

    private final SpaceRepository spaceRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = requestPathWithinApplication(request);
        Matcher matcher = SPACE_PATH.matcher(path);
        if (matcher.matches()) {
            String slug = matcher.group(1);
            // Exclude management endpoints that are not slug-bound
            if (!isManagementEndpoint(slug)) {
                Optional<Space> spaceOptional = spaceRepository.findBySlug(slug);
                if (spaceOptional.isEmpty()) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Space not found: " + slug + "\"}");
                    return;
                }
                Space space = spaceOptional.get();
                SpaceContextHolder.set(space.getId());
                request.setAttribute(SPACE_SLUG_ATTRIBUTE, space.getSlug());
                request.setAttribute(SPACE_ID_ATTRIBUTE, space.getId());
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SpaceContextHolder.clear();
        }
    }

    private boolean isManagementEndpoint(String slugCandidate) {
        // Reserved words that aren't slugs: none currently. Kept as a hook.
        return false;
    }

    private String requestPathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            return path.substring(contextPath.length());
        }
        return path;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
