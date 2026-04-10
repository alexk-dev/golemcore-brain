package dev.golemcore.brain.application.port.out.auth;

import dev.golemcore.brain.domain.auth.UserSession;
import java.util.Optional;

public interface SessionRepository {

    Optional<UserSession> findByToken(String token);

    UserSession save(UserSession session);

    void delete(String token);

    void deleteByUserId(String userId);
}
