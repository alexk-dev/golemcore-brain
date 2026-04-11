package dev.golemcore.brain.web;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.space.SpaceContextHolder;
import dev.golemcore.brain.domain.space.Space;
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
        String path = request.getRequestURI();
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

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
