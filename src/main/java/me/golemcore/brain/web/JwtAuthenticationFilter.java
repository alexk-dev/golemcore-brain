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

import me.golemcore.brain.application.port.out.ApiKeyTokenPort;
import me.golemcore.brain.application.service.apikey.ApiKeyService;
import me.golemcore.brain.application.service.auth.AuthService;
import me.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import me.golemcore.brain.domain.apikey.ApiKey;
import me.golemcore.brain.domain.auth.AuthContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_CONTEXT_ATTRIBUTE = "brain.authContext";

    private final ApiKeyTokenPort apiKeyTokenPort;
    private final ApiKeyService apiKeyService;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length()).trim();
            try {
                ApiKeyTokenPort.ParsedApiKeyToken parsed = apiKeyTokenPort.parse(token);
                ApiKey apiKey = apiKeyService.findActive(parsed.jti());
                if (apiKey == null) {
                    sendUnauthorized(response, "API key revoked or expired");
                    return;
                }
                AuthContext authContext = authService.apiKeyContext(parsed.subject(), parsed.spaceId(), parsed.roles());
                request.setAttribute(AUTH_CONTEXT_ATTRIBUTE, authContext);
            } catch (AuthUnauthorizedException exception) {
                sendUnauthorized(response, exception.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
    }
}
