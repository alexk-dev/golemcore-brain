package dev.golemcore.brain.domain.space;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Space {
    String id;
    String slug;
    String name;
    Instant createdAt;
}
