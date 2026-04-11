package dev.golemcore.brain.adapter.out.jwt;

import dev.golemcore.brain.application.port.out.ApiKeyTokenPort;
import dev.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.apikey.ApiKey;
import dev.golemcore.brain.domain.auth.UserRole;
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
