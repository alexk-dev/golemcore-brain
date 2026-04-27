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

package me.golemcore.brain.application.service.audit;

import me.golemcore.brain.domain.auth.AuthContext;
import me.golemcore.brain.domain.auth.PublicUserView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Append-only audit trail for security-sensitive admin actions. Writes
 * structured one-line records to the {@code audit} SLF4J logger; route that
 * logger to its own appender via logback config when forensic separation is
 * required. Lines are intentionally simple (no JSON deps) so they survive
 * grepping in any log shipper.
 */
public class AuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("audit");

    public void userCreated(AuthContext actor, String targetUserId, String targetUsername) {
        emit(actor, "user.create", "userId=" + targetUserId + " username=" + targetUsername);
    }

    public void userUpdated(AuthContext actor, String targetUserId, String fieldsChanged) {
        emit(actor, "user.update", "userId=" + targetUserId + " fields=" + fieldsChanged);
    }

    public void userDeleted(AuthContext actor, String targetUserId) {
        emit(actor, "user.delete", "userId=" + targetUserId);
    }

    public void apiKeyIssued(AuthContext actor, String keyId, String spaceId, String roles) {
        emit(actor, "apikey.issue", "keyId=" + keyId + " spaceId=" + spaceId + " roles=" + roles);
    }

    public void apiKeyRevoked(AuthContext actor, String keyId) {
        emit(actor, "apikey.revoke", "keyId=" + keyId);
    }

    public void loginThrottled(String ip, String identifier) {
        AUDIT.warn("event=login.throttled ip={} identifier={}", sanitize(ip), sanitize(identifier));
    }

    private void emit(AuthContext actor, String event, String details) {
        AUDIT.info("event={} actor={} apiKey={} {}",
                event,
                actorIdentity(actor),
                actor != null && actor.isApiKey(),
                sanitize(details));
    }

    private static String actorIdentity(AuthContext actor) {
        if (actor == null) {
            return "anonymous";
        }
        PublicUserView user = actor.getUser();
        if (user == null) {
            return "anonymous";
        }
        return user.getUsername() == null ? user.getId() : user.getUsername();
    }

    /**
     * Strip CR/LF from any field that flows from user input to prevent log
     * injection.
     */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }
}
