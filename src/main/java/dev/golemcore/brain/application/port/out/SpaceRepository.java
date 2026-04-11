package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.space.Space;
import java.util.List;
import java.util.Optional;

public interface SpaceRepository {

    void initialize();

    List<Space> listSpaces();

    Optional<Space> findById(String id);

    Optional<Space> findBySlug(String slug);

    Space save(Space space);

    void delete(String id);
}
