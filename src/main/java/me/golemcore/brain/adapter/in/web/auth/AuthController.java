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

import me.golemcore.brain.adapter.in.web.auth.dto.ChangePasswordRequest;
import me.golemcore.brain.adapter.in.web.auth.dto.CreateUserRequest;
import me.golemcore.brain.adapter.in.web.auth.dto.LoginRequest;
import me.golemcore.brain.adapter.in.web.auth.dto.UpdateUserRequest;
import me.golemcore.brain.application.service.auth.AuthService;
import me.golemcore.brain.application.service.user.UserManagementService;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.auth.AuthConfigResponse;
import me.golemcore.brain.domain.auth.AuthResponse;
import me.golemcore.brain.domain.auth.PublicUserView;
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
