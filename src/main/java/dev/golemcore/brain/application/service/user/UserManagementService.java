package dev.golemcore.brain.application.service.user;

import dev.golemcore.brain.application.port.out.auth.UserRepository;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.application.service.auth.PasswordHasher;
import dev.golemcore.brain.domain.auth.PublicUserView;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.auth.WikiUser;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthService authService;

    public java.util.List<PublicUserView> listUsers(Optional<String> sessionToken) {
        return authService.listUsers(sessionToken);
    }

    public PublicUserView createUser(Optional<String> sessionToken, String username, String email, String password, UserRole role) {
        authService.requireUserManagement(sessionToken);
        userRepository.findByUsernameOrEmail(username).ifPresent(existing -> {
            throw new IllegalArgumentException("User already exists: " + username);
        });
        WikiUser user = WikiUser.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordHasher.hash(password))
                .role(role)
                .build();
        userRepository.save(user);
        return PublicUserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
