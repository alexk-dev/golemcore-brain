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

package me.golemcore.brain.application.service.user;

import me.golemcore.brain.application.port.out.auth.SessionRepository;
import me.golemcore.brain.application.port.out.auth.UserRepository;
import me.golemcore.brain.application.service.auth.AuthService;
import me.golemcore.brain.application.service.auth.PasswordHasher;
import me.golemcore.brain.domain.auth.PublicUserView;
import me.golemcore.brain.domain.auth.UserRole;
import me.golemcore.brain.domain.auth.WikiUser;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserManagementService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthService authService;
    private final SessionRepository sessionRepository;

    public java.util.List<PublicUserView> listUsers(Optional<String> sessionToken) {
        return authService.listUsers(sessionToken);
    }

    public PublicUserView createUser(Optional<String> sessionToken, String username, String email, String password,
            UserRole role) {
        authService.requireUserManagement(sessionToken);
        assertUniqueIdentity(username, email, null);
        WikiUser user = WikiUser.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordHasher.hash(password))
                .role(role)
                .build();
        userRepository.save(user);
        return toPublicView(user);
    }

    public PublicUserView updateUser(
            Optional<String> sessionToken,
            String userId,
            String username,
            String email,
            String password,
            UserRole role) {
        authService.requireUserManagement(sessionToken);
        WikiUser existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        PublicUserView actingUser = authService.requireAuthenticated(sessionToken).getUser();
        validateSelfAdminChange(existingUser, actingUser, role);
        assertUniqueIdentity(username, email, userId);
        String nextPasswordHash = password == null || password.isBlank()
                ? existingUser.getPasswordHash()
                : passwordHasher.hash(password);
        WikiUser updatedUser = WikiUser.builder()
                .id(existingUser.getId())
                .username(username)
                .email(email)
                .passwordHash(nextPasswordHash)
                .role(role)
                .build();
        userRepository.save(updatedUser);
        if (!nextPasswordHash.equals(existingUser.getPasswordHash())) {
            sessionRepository.deleteByUserId(userId);
        }
        return toPublicView(updatedUser);
    }

    public void deleteUser(Optional<String> sessionToken, String userId) {
        authService.requireUserManagement(sessionToken);
        PublicUserView actingUser = authService.requireAuthenticated(sessionToken).getUser();
        if (actingUser != null && actingUser.getId().equals(userId)) {
            throw new IllegalArgumentException("You cannot delete your own user");
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        userRepository.delete(userId);
        sessionRepository.deleteByUserId(userId);
    }

    private void assertUniqueIdentity(String username, String email, String excludedUserId) {
        userRepository.findByUsernameOrEmail(username)
                .filter(user -> excludedUserId == null || !user.getId().equals(excludedUserId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("User already exists: " + username);
                });
        userRepository.findByUsernameOrEmail(email)
                .filter(user -> excludedUserId == null || !user.getId().equals(excludedUserId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("User already exists: " + email);
                });
    }

    private void validateSelfAdminChange(WikiUser existingUser, PublicUserView actingUser, UserRole nextRole) {
        if (actingUser == null) {
            return;
        }
        if (!actingUser.getId().equals(existingUser.getId())) {
            return;
        }
        if (actingUser.getRole() == UserRole.ADMIN && nextRole != UserRole.ADMIN) {
            throw new IllegalArgumentException("You cannot remove your own admin role");
        }
    }

    private PublicUserView toPublicView(WikiUser user) {
        return PublicUserView.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
