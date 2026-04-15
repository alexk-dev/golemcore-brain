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

package me.golemcore.brain.adapter.in.web.auth;

import me.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import me.golemcore.brain.application.service.auth.AuthService;
import me.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import me.golemcore.brain.domain.auth.AuthContext;
import me.golemcore.brain.domain.auth.UserRole;
import me.golemcore.brain.web.JwtAuthenticationFilter;
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
