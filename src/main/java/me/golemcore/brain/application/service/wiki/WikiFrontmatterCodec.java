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

package me.golemcore.brain.application.service.wiki;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses and renders the small YAML frontmatter subset used by wiki pages.
 *
 * <p>
 * Supported keys are currently {@code tags} and {@code summary}. Any page body
 * without a matching frontmatter header is returned unchanged.
 */
public class WikiFrontmatterCodec {

    private static final Pattern FRONTMATTER_PATTERN = Pattern
            .compile("^---\\s*\\r?\\n(.*?)\\r?\\n---\\s*\\r?\\n", Pattern.DOTALL);

    private static final int MAX_TAGS = 32;
    private static final int MAX_TAG_LENGTH = 64;
    private static final int MAX_SUMMARY_LENGTH = 1000;

    public Frontmatter parse(String body) {
        if (body == null || body.isEmpty()) {
            return new Frontmatter(List.of(), null, "");
        }
        Matcher matcher = FRONTMATTER_PATTERN.matcher(body);
        if (!matcher.find() || matcher.start() != 0) {
            return new Frontmatter(List.of(), null, body);
        }
        String yaml = matcher.group(1);
        List<String> tags = new ArrayList<>();
        String summary = null;
        for (String line : yaml.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("tags:")) {
                String value = trimmed.substring("tags:".length()).trim();
                if (value.startsWith("[") && value.endsWith("]")) {
                    String inner = value.substring(1, value.length() - 1);
                    for (String item : splitQuotedCsv(inner)) {
                        String cleaned = item.trim();
                        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
                            cleaned = unescapeValue(cleaned.substring(1, cleaned.length() - 1));
                        }
                        if (!cleaned.isEmpty()) {
                            tags.add(cleaned);
                        }
                    }
                }
            } else if (trimmed.startsWith("summary:")) {
                String value = trimmed.substring("summary:".length()).trim();
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = unescapeValue(value.substring(1, value.length() - 1));
                }
                summary = value;
            }
        }
        String remaining = body.substring(matcher.end()).stripTrailing();
        return new Frontmatter(tags, summary, remaining);
    }

    public String render(List<String> tags, String summary, String body) {
        validate(tags, summary);
        boolean hasTags = tags != null && !tags.isEmpty();
        boolean hasSummary = summary != null && !summary.isBlank();
        if (!hasTags && !hasSummary) {
            return Optional.ofNullable(body).orElse("");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        if (hasTags) {
            sb.append("tags: [");
            sb.append(tags.stream()
                    .map(tag -> "\"" + escapeValue(tag) + "\"")
                    .collect(Collectors.joining(", ")));
            sb.append("]\n");
        }
        if (hasSummary) {
            sb.append("summary: \"").append(escapeValue(summary)).append("\"\n");
        }
        sb.append("---\n");
        sb.append(Optional.ofNullable(body).orElse(""));
        return sb.toString();
    }

    public void validate(List<String> tags, String summary) {
        if (tags != null) {
            if (tags.size() > MAX_TAGS) {
                throw new IllegalArgumentException(
                        "tags exceeds maximum of " + MAX_TAGS + " entries (got " + tags.size() + ")");
            }
            for (String tag : tags) {
                if (tag != null && tag.length() > MAX_TAG_LENGTH) {
                    throw new IllegalArgumentException(
                            "tag exceeds maximum length of " + MAX_TAG_LENGTH + " characters");
                }
            }
        }
        if (summary != null && summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException(
                    "summary exceeds maximum length of " + MAX_SUMMARY_LENGTH + " characters");
        }
    }

    private static String escapeValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeValue(String escaped) {
        StringBuilder out = new StringBuilder(escaped.length());
        for (int index = 0; index < escaped.length(); index++) {
            char ch = escaped.charAt(index);
            if (ch == '\\' && index + 1 < escaped.length()) {
                char next = escaped.charAt(index + 1);
                if (next == '\\' || next == '\"') {
                    out.append(next);
                    index++;
                    continue;
                }
            }
            out.append(ch);
        }
        return out.toString();
    }

    private static List<String> splitQuotedCsv(String inner) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < inner.length(); index++) {
            char ch = inner.charAt(index);
            if (ch == '\\' && index + 1 < inner.length()) {
                current.append(ch).append(inner.charAt(index + 1));
                index++;
                continue;
            }
            if (ch == '\"') {
                inQuotes = !inQuotes;
                current.append(ch);
                continue;
            }
            if (ch == ',' && !inQuotes) {
                items.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            items.add(current.toString());
        }
        return items;
    }

    public record Frontmatter(List<String> tags, String summary, String remainingBody) {
    }
}
