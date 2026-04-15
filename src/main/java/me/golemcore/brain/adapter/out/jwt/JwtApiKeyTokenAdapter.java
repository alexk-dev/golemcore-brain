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

package me.golemcore.brain.adapter.out.jwt;

import me.golemcore.brain.application.port.out.ApiKeyTokenPort;
import me.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.apikey.ApiKey;
import me.golemcore.brain.domain.auth.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtApiKeyTokenAdapter implements ApiKeyTokenPort {

    private final WikiProperties wikiProperties;

    @Override
    public String issue(ApiKey apiKey) {
        JwtBuilder builder = Jwts.builder()
                .id(apiKey.getId())
                .issuer(wikiProperties.getJwt().getIssuer())
                .subject(apiKey.getSubject())
                .issuedAt(Date.from(apiKey.getCreatedAt()))
                .claim("name", apiKey.getName())
                .claim("space", apiKey.getSpaceId())
                .claim("roles", apiKey.getRoles().stream().map(UserRole::name).toList());
        if (apiKey.getExpiresAt() != null) {
            builder.expiration(Date.from(apiKey.getExpiresAt()));
        }
        return builder.signWith(signingKey()).compact();
    }

    @Override
    public ApiKeyTokenPort.ParsedApiKeyToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(wikiProperties.getJwt().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String spaceId = claims.get("space", String.class);
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles", List.class);
            Set<UserRole> parsedRoles = roles == null
                    ? Set.of()
                    : roles.stream().map(UserRole::valueOf).collect(Collectors.toUnmodifiableSet());
            Instant expiresAt = claims.getExpiration() == null ? null : claims.getExpiration().toInstant();
            return new ApiKeyTokenPort.ParsedApiKeyToken(claims.getId(), claims.getSubject(), spaceId, parsedRoles,
                    expiresAt);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthUnauthorizedException("Invalid JWT: " + exception.getMessage());
        }
    }

    private SecretKey signingKey() {
        String secret = wikiProperties.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("brain.jwt.secret must be at least 32 characters");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

}
