package dev.golemcore.brain.application.port.out.auth;

import dev.golemcore.brain.domain.auth.WikiUser;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    void initialize();

    List<WikiUser> listUsers();

    Optional<WikiUser> findById(String userId);

    Optional<WikiUser> findByUsernameOrEmail(String identifier);

    WikiUser save(WikiUser user);

    WikiUser createAdminIfMissing(String username, String email, String passwordHash);

    void delete(String userId);
}
