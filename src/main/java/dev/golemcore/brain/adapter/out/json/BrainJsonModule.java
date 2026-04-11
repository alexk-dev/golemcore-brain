package dev.golemcore.brain.adapter.out.json;

import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import java.io.Serial;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

@Component
public class BrainJsonModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("PMD.ConstructorCallsOverridableMethod")
    public BrainJsonModule() {
        registerSerializersAndDeserializers();
    }

    private void registerSerializersAndDeserializers() {
        addSerializer(Secret.class, new SecretSerializer());
        addDeserializer(Secret.class, new SecretDeserializer());
        addSerializer(LlmApiType.class, new LlmApiTypeSerializer());
        addDeserializer(LlmApiType.class, new LlmApiTypeDeserializer());
        addSerializer(LlmModelKind.class, new LlmModelKindSerializer());
        addDeserializer(LlmModelKind.class, new LlmModelKindDeserializer());
    }

    private static class SecretSerializer extends ValueSerializer<Secret> {
        @Override
        public void serialize(Secret value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            if (value == null) {
                generator.writeNull();
                return;
            }
            generator.writeStartObject();
            generator.writeStringProperty("value", value.getValue());
            generator.writeBooleanProperty("encrypted", Boolean.TRUE.equals(value.getEncrypted()));
            generator.writeBooleanProperty("present", Boolean.TRUE.equals(value.getPresent()));
            generator.writeEndObject();
        }
    }

    private static class SecretDeserializer extends ValueDeserializer<Secret> {
        @Override
        public Secret deserialize(tools.jackson.core.JsonParser parser, DeserializationContext context)
                throws JacksonException {
            JsonNode node = context.readTree(parser);
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            if (node.isTextual()) {
                return Secret.of(node.asString());
            }
            if (node.isObject()) {
                String value = node.path("value").isMissingNode() || node.path("value").isNull()
                        ? null
                        : node.path("value").asString();
                boolean encrypted = node.path("encrypted").asBoolean(false);
                boolean present = node.path("present").asBoolean(value != null && !value.isBlank());
                return Secret.builder()
                        .value(value)
                        .encrypted(encrypted)
                        .present(present)
                        .build();
            }
            return Secret.of(node.asString());
        }
    }

    private static class LlmApiTypeSerializer extends ValueSerializer<LlmApiType> {
        @Override
        public void serialize(LlmApiType value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(value == null ? null : value.getValue());
        }
    }

    private static class LlmApiTypeDeserializer extends ValueDeserializer<LlmApiType> {
        @Override
        public LlmApiType deserialize(tools.jackson.core.JsonParser parser, DeserializationContext context) {
            return LlmApiType.fromJson(parser.getValueAsString(null));
        }
    }

    private static class LlmModelKindSerializer extends ValueSerializer<LlmModelKind> {
        @Override
        public void serialize(LlmModelKind value, JsonGenerator generator, SerializationContext context)
                throws JacksonException {
            generator.writeString(value == null ? null : value.getValue());
        }
    }

    private static class LlmModelKindDeserializer extends ValueDeserializer<LlmModelKind> {
        @Override
        public LlmModelKind deserialize(tools.jackson.core.JsonParser parser, DeserializationContext context) {
            return LlmModelKind.fromJson(parser.getValueAsString(null));
        }
    }
}
