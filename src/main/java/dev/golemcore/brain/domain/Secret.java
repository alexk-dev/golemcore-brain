package dev.golemcore.brain.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Secret {

    private String value;

    @Builder.Default
    private Boolean encrypted = false;

    @Builder.Default
    private Boolean present = false;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Secret fromJson(Object source) {
        if (source == null) {
            return null;
        }

        if (source instanceof String value) {
            return Secret.builder()
                    .value(value)
                    .encrypted(false)
                    .present(!value.isBlank())
                    .build();
        }

        if (source instanceof Map<?, ?> map) {
            Object valueObject = map.get("value");
            Object encryptedObject = map.get("encrypted");
            Object presentObject = map.get("present");
            String value = valueObject != null ? String.valueOf(valueObject) : null;
            boolean encrypted = encryptedObject instanceof Boolean encryptedValue && encryptedValue;
            boolean present = presentObject instanceof Boolean presentValue && presentValue;
            if (!present && value != null && !value.isBlank()) {
                present = true;
            }
            return Secret.builder()
                    .value(value)
                    .encrypted(encrypted)
                    .present(present)
                    .build();
        }

        String value = String.valueOf(source);
        return Secret.builder()
                .value(value)
                .encrypted(false)
                .present(!value.isBlank())
                .build();
    }

    public static Secret of(String value) {
        return fromJson(value);
    }

    public static Secret redacted(Secret source) {
        if (source == null) {
            return null;
        }
        boolean isPresent = Boolean.TRUE.equals(source.getPresent())
                || (source.getValue() != null && !source.getValue().isBlank());
        return Secret.builder()
                .value(null)
                .encrypted(Boolean.TRUE.equals(source.getEncrypted()))
                .present(isPresent)
                .build();
    }

    public static String valueOrEmpty(Secret secret) {
        if (secret == null || secret.getValue() == null) {
            return "";
        }
        return secret.getValue();
    }

    public static boolean hasValue(Secret secret) {
        return secret != null && secret.getValue() != null && !secret.getValue().isBlank();
    }
}
