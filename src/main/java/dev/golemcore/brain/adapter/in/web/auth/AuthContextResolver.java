package dev.golemcore.brain.adapter.in.web.auth;

import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.web.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Unified entry point for resolving the current request's AuthContext,
 * preferring a JWT-based context set by {@link JwtAuthenticationFilter} and
 * falling back to session cookies.
 */
@Component
@RequiredArgsConstructor
public class AuthContextResolver {

    private final AuthService authService;
    private final AuthCookieHelper authCookieHelper;

    public AuthContext resolve(HttpServletRequest request) {
        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_CONTEXT_ATTRIBUTE);
        if (attribute instanceof AuthContext context) {
            return context;
        }
        return authService.resolveContext(authCookieHelper.readSessionToken(request));
    }

    public AuthContext requireAuthenticated(HttpServletRequest request) {
        AuthContext context = resolve(request);
        if (!context.isAuthenticated() && !context.isAuthDisabled()) {
            throw new AuthUnauthorizedException("Authentication required");
        }
        return context;
    }

    public AuthContext requireSpaceAccess(HttpServletRequest request, String spaceId, UserRole required) {
        AuthContext context = resolve(request);
        if (!context.isAuthDisabled() && !context.isAuthenticated() && !context.isReadOnlyAnonymous()) {
            throw new AuthUnauthorizedException("Authentication required");
        }
        if (!context.canAccessSpace(spaceId, required)) {
            throw new AuthAccessDeniedException("Access to space denied");
        }
        return context;
    }
}
