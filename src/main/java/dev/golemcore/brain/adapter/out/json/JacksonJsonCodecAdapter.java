package dev.golemcore.brain.adapter.out.json;

import dev.golemcore.brain.application.port.out.JsonCodecPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JacksonJsonCodecAdapter implements JsonCodecPort {

    private final ObjectMapper objectMapper;

    @Override
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize JSON", exception);
        }
    }

    @Override
    public Object read(String content) {
        try {
            return objectMapper.readValue(content, Object.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid JSON", exception);
        }
    }
}
