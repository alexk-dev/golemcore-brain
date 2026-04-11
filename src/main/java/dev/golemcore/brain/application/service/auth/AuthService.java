package dev.golemcore.brain.application.service.auth;

import dev.golemcore.brain.adapter.out.filesystem.auth.FileUserRepository;
import dev.golemcore.brain.application.port.out.auth.SessionRepository;
import dev.golemcore.brain.application.port.out.auth.UserRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.auth.AuthConfigResponse;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.AuthResponse;
import dev.golemcore.brain.domain.auth.PublicUserView;
import dev.golemcore.brain.domain.auth.SpaceMembership;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.auth.UserSession;
import dev.golemcore.brain.domain.auth.WikiUser;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String SESSION_COOKIE_NAME = "BRAIN_SESSION";

    private final WikiProperties wikiProperties;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordHasher passwordHasher;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void initialize() {
        userRepository.initialize();
        if (userRepository instanceof FileUserRepository fileUserRepository) {
            fileUserRepository.createAdminIfMissing(
                    wikiProperties.getAdminUsername(),
                    wikiProperties.getAdminEmail(),
                    passwordHasher.hash(wikiProperties.getAdminPassword()));
        }
    }

    public AuthConfigResponse getConfig(Optional<String> sessionToken) {
        AuthContext authContext = resolveContext(sessionToken);
        return AuthConfigResponse.builder()
                .authDisabled(wikiProperties.isAuthDisabled())
                .publicAccess(wikiProperties.isPublicAccess())
                .user(authContext.getUser())
                .build();
    }

    public AuthResponse login(String identifier, String password) {
        WikiUser user = userRepository.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new AuthUnauthorizedException("Invalid credentials"));
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthUnauthorizedException("Invalid credentials");
        }
        sessionRepository.deleteByUserId(user.getId());
        UserSession session = createSession(user.getId());
        sessionRepository.save(session);
        return AuthResponse.builder()
                .message(session.getToken())
                .user(toPublicView(user))
                .build();
    }

    public AuthResponse changePassword(Optional<String> sessionToken, String currentPassword, String newPassword) {
        AuthContext authContext = requireAuthenticated(sessionToken);
        PublicUserView currentUser = authContext.getUser();
        if (currentUser == null) {
            throw new AuthUnauthorizedException("Authentication required");
        }
        WikiUser user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new AuthUnauthorizedException("User not found"));
        if (!passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthUnauthorizedException("Current password is incorrect");
        }
        WikiUser updatedUser = user.toBuilder()
                .passwordHash(passwordHasher.hash(newPassword))
                .build();
        userRepository.save(updatedUser);
        sessionRepository.deleteByUserId(user.getId());
        return AuthResponse.builder()
                .message("Password changed")
                .user(null)
                .build();
    }

    public void logout(Optional<String> sessionToken) {
        sessionToken.ifPresent(sessionRepository::delete);
    }

    public AuthContext requireContext(Optional<String> sessionToken) {
        return resolveContext(sessionToken);
    }

    public AuthContext requireAuthenticated(Optional<String> sessionToken) {
        AuthContext authContext = resolveContext(sessionToken);
        if (!authContext.isAuthenticated() && !wikiProperties.isAuthDisabled()) {
            throw new AuthUnauthorizedException("Authentication required");
        }
        return authContext;
    }

    public void requireEditAccess(Optional<String> sessionToken) {
        AuthContext authContext = resolveContext(sessionToken);
        if (!authContext.canEdit()) {
            throw new AuthAccessDeniedException("Edit access is required");
        }
    }

    public void requireUserManagement(Optional<String> sessionToken) {
        AuthContext authContext = resolveContext(sessionToken);
        if (!authContext.canManageUsers()) {
            throw new AuthAccessDeniedException("Admin access is required");
        }
    }

    public List<PublicUserView> listUsers(Optional<String> sessionToken) {
        requireUserManagement(sessionToken);
        return userRepository.listUsers().stream().map(this::toPublicView).toList();
    }

    public AuthContext resolveContext(Optional<String> sessionToken) {
        if (wikiProperties.isAuthDisabled()) {
            return AuthContext.builder()
                    .authDisabled(true)
                    .publicAccess(wikiProperties.isPublicAccess())
                    .authenticated(false)
                    .user(null)
                    .memberships(List.of())
                    .build();
        }
        if (sessionToken.isEmpty()) {
            return anonymousContext();
        }
        Optional<UserSession> session = sessionRepository.findByToken(sessionToken.get());
        if (session.isEmpty()) {
            return anonymousContext();
        }
        WikiUser user = userRepository.findById(session.get().getUserId())
                .orElseThrow(() -> new AuthUnauthorizedException("User for session not found"));
        return AuthContext.builder()
                .authDisabled(false)
                .publicAccess(wikiProperties.isPublicAccess())
                .authenticated(true)
                .user(toPublicView(user))
                .memberships(user.getMemberships())
                .build();
    }

    public AuthContext apiKeyContext(String subject, String pinnedSpaceId, java.util.Set<UserRole> roles) {
        List<SpaceMembership> memberships = roles.stream()
                .map(role -> SpaceMembership.builder().spaceId(pinnedSpaceId).role(role).build())
                .toList();
        PublicUserView user = null;
        if (subject != null && subject.startsWith("user:")) {
            String userId = subject.substring("user:".length());
            user = userRepository.findById(userId).map(this::toPublicView).orElse(null);
        }
        return AuthContext.builder()
                .authDisabled(false)
                .publicAccess(wikiProperties.isPublicAccess())
                .authenticated(true)
                .apiKey(true)
                .pinnedSpaceId(pinnedSpaceId)
                .user(user)
                .memberships(memberships)
                .build();
    }

    private AuthContext anonymousContext() {
        return AuthContext.builder()
                .authDisabled(false)
                .publicAccess(wikiProperties.isPublicAccess())
                .authenticated(false)
                .user(null)
                .memberships(List.of())
                .build();
    }

    private UserSession createSession(String userId) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return UserSession.builder()
                .token(token)
                .userId(userId)
                .expiresAt(Instant.now().plusSeconds(wikiProperties.getSessionTtlSeconds()))
                .build();
    }

    private PublicUserView toPublicView(WikiUser user) {
        PublicUserView.PublicUserViewBuilder builder = PublicUserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole());
        for (SpaceMembership membership : user.getMemberships()) {
            builder.membership(membership);
        }
        return builder.build();
    }
}
