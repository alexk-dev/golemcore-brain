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
import java.util.Optional;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class WikiUser {
    String id;
    String username;
    String email;
    String passwordHash;
    /**
     * Legacy single-role field kept for compatibility; use memberships for
     * authorization.
     */
    UserRole role;
    @Singular
    List<SpaceMembership> memberships;

    public boolean isGlobalAdmin() {
        return memberships.stream().anyMatch(m -> m.isGlobal() && m.getRole() == UserRole.ADMIN);
    }

    public Optional<UserRole> effectiveRole(String spaceId) {
        UserRole best = null;
        for (SpaceMembership membership : memberships) {
            if (membership.isGlobal() || membership.getSpaceId().equals(spaceId)) {
                if (best == null || rank(membership.getRole()) > rank(best)) {
                    best = membership.getRole();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static int rank(UserRole role) {
        return switch (role) {
        case ADMIN -> 3;
        case EDITOR -> 2;
        case VIEWER -> 1;
        };
    }
}
