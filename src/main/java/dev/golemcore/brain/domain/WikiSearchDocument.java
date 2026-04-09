package dev.golemcore.brain.domain;

import java.util.Locale;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiSearchDocument {
    String id;
    String path;
    String parentPath;
    String title;
    String body;
    WikiNodeKind kind;

    public boolean matches(String query) {
        String normalizedText = (title + "\n" + body).toLowerCase(Locale.ROOT);
        return normalizedText.contains(query);
    }

    public String buildExcerpt(String query) {
        if (body == null || body.isBlank()) {
            return "No additional content";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        String normalizedBody = compact.toLowerCase(Locale.ROOT);
        int index = normalizedBody.indexOf(query);
        if (index < 0) {
            return compact.length() > 180 ? compact.substring(0, 177) + "..." : compact;
        }
        int start = Math.max(0, index - 60);
        int end = Math.min(compact.length(), index + query.length() + 110);
        String excerpt = compact.substring(start, end);
        if (start > 0) {
            excerpt = "..." + excerpt;
        }
        if (end < compact.length()) {
            excerpt = excerpt + "...";
        }
        return excerpt;
    }
}
