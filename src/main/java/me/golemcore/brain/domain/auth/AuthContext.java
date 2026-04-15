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

package me.golemcore.brain.domain.auth;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthContext {
    boolean authDisabled;
    boolean publicAccess;
    boolean authenticated;
    PublicUserView user;
    /**
     * Effective memberships for this context. For session-based auth these come
     * from the user record; for JWT API keys these are synthesised from the key
     * scope.
     */
    @Builder.Default
    List<SpaceMembership> memberships = List.of();
    /**
     * Whether this context originated from a JWT API key (true) or a session cookie
     * (false).
     */
    @Builder.Default
    boolean apiKey = false;
    /**
     * For API key contexts, the space the key is pinned to, or null if the key is
     * global.
     */
    String pinnedSpaceId;

    public boolean isReadOnlyAnonymous() {
        return !authDisabled && !authenticated && publicAccess;
    }

    public boolean isGlobalAdmin() {
        if (authDisabled) {
            return true;
        }
        return memberships.stream().anyMatch(m -> m.isGlobal() && m.getRole() == UserRole.ADMIN);
    }

    public boolean canEdit() {
        if (authDisabled) {
            return true;
        }
        return memberships.stream().anyMatch(m -> m.isGlobal() && m.getRole().canEdit());
    }

    public boolean canManageUsers() {
        if (authDisabled) {
            return true;
        }
        return memberships.stream().anyMatch(m -> m.isGlobal() && m.getRole() == UserRole.ADMIN);
    }

    public boolean canAccessSpace(String spaceId, UserRole required) {
        if (authDisabled) {
            return true;
        }
        if (apiKey && pinnedSpaceId != null && !pinnedSpaceId.equals(spaceId)) {
            return false;
        }
        if (publicAccess && required == UserRole.VIEWER) {
            return true;
        }
        int requiredRank = rank(required);
        for (SpaceMembership membership : memberships) {
            if (membership.isGlobal() || membership.getSpaceId().equals(spaceId)) {
                if (rank(membership.getRole()) >= requiredRank) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<String> accessibleSpaceIds(Set<String> allSpaceIds) {
        if (authDisabled) {
            return allSpaceIds;
        }
        boolean hasGlobal = memberships.stream().anyMatch(SpaceMembership::isGlobal);
        if (hasGlobal) {
            return allSpaceIds;
        }
        return memberships.stream()
                .map(SpaceMembership::getSpaceId)
                .filter(allSpaceIds::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static int rank(UserRole role) {
        return switch (role) {
        case ADMIN -> 3;
        case EDITOR -> 2;
        case VIEWER -> 1;
        };
    }
}
