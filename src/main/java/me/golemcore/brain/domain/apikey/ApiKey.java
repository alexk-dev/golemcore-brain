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

package me.golemcore.brain.domain.apikey;

import me.golemcore.brain.domain.auth.UserRole;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiKey {
    /** JWT id (jti). */
    String id;
    String name;
    /** Owning user id, or "service:xxx" for machine keys. */
    String subject;
    /** Null = global key (access to all spaces). */
    String spaceId;
    /** Permissions granted by this key within its scope. */
    Set<UserRole> roles;
    Instant createdAt;
    /** Null = non-expiring. */
    Instant expiresAt;
    boolean revoked;

    public boolean isGlobal() {
        return spaceId == null;
    }
}
