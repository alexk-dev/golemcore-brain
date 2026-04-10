package dev.golemcore.brain.adapter.in.web.auth;

import dev.golemcore.brain.adapter.in.web.auth.dto.ChangePasswordRequest;
import dev.golemcore.brain.adapter.in.web.auth.dto.CreateUserRequest;
import dev.golemcore.brain.adapter.in.web.auth.dto.LoginRequest;
import dev.golemcore.brain.adapter.in.web.auth.dto.UpdateUserRequest;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.application.service.user.UserManagementService;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.auth.AuthConfigResponse;
import dev.golemcore.brain.domain.auth.AuthResponse;
import dev.golemcore.brain.domain.auth.PublicUserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieHelper authCookieHelper;
    private final UserManagementService userManagementService;
    private final WikiProperties wikiProperties;

    @GetMapping("/config")
    public AuthConfigResponse getConfig(HttpServletRequest request) {
        return authService.getConfig(authCookieHelper.readSessionToken(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest requestBody, HttpServletResponse response) {
        AuthResponse authResponse = authService.login(requestBody.getIdentifier(), requestBody.getPassword());
        authCookieHelper.writeSessionToken(response, authResponse.getMessage(), wikiProperties.getSessionTtlSeconds());
        return AuthResponse.builder()
                .message("Logged in")
                .user(authResponse.getUser())
                .build();
    }

    @PostMapping("/logout")
    public AuthResponse logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(authCookieHelper.readSessionToken(request));
        authCookieHelper.clearSessionToken(response);
        return AuthResponse.builder().message("Logged out").user(null).build();
    }

    @PostMapping("/password")
    public AuthResponse changePassword(
            @Valid @RequestBody ChangePasswordRequest requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthResponse authResponse = authService.changePassword(
                authCookieHelper.readSessionToken(request),
                requestBody.getCurrentPassword(),
                requestBody.getNewPassword());
        authCookieHelper.clearSessionToken(response);
        return authResponse;
    }

    @GetMapping("/me")
    public AuthConfigResponse me(HttpServletRequest request) {
        return authService.getConfig(authCookieHelper.readSessionToken(request));
    }

    @GetMapping("/users")
    public List<PublicUserView> listUsers(HttpServletRequest request) {
        return userManagementService.listUsers(authCookieHelper.readSessionToken(request));
    }

    @PostMapping("/users")
    public PublicUserView createUser(@Valid @RequestBody CreateUserRequest requestBody, HttpServletRequest request) {
        return userManagementService.createUser(
                authCookieHelper.readSessionToken(request),
                requestBody.getUsername(),
                requestBody.getEmail(),
                requestBody.getPassword(),
                requestBody.getRole());
    }

    @PutMapping("/users/{userId}")
    public PublicUserView updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest requestBody,
            HttpServletRequest request) {
        return userManagementService.updateUser(
                authCookieHelper.readSessionToken(request),
                userId,
                requestBody.getUsername(),
                requestBody.getEmail(),
                requestBody.getPassword(),
                requestBody.getRole());
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@PathVariable String userId, HttpServletRequest request) {
        userManagementService.deleteUser(authCookieHelper.readSessionToken(request), userId);
    }
}
