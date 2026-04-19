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

import me.golemcore.brain.domain.WikiPatchOperation;
import java.util.Optional;

/**
 * Applies page-body patch operations used by the wiki editing API.
 */
public class WikiPatchApplier {

    public String apply(String currentBody, WikiPatchRequest request) {
        String original = Optional.ofNullable(currentBody).orElse("");
        String addition = Optional.ofNullable(request.getContent()).orElse("");
        switch (request.getOperation()) {
        case APPEND:
            return original + addition;
        case PREPEND:
            return addition + original;
        case REPLACE_SECTION:
            String heading = Optional.ofNullable(request.getHeading()).orElse("").trim();
            if (heading.isBlank()) {
                throw new IllegalArgumentException("heading is required for REPLACE_SECTION");
            }
            return replaceSection(original, heading, addition);
        default:
            throw new IllegalArgumentException("Unsupported patch operation: " + request.getOperation());
        }
    }

    public String buildSummary(WikiPatchRequest request, String before, String after) {
        int deltaChars = (after == null ? 0 : after.length()) - (before == null ? 0 : before.length());
        if (deltaChars == 0) {
            switch (request.getOperation()) {
            case REPLACE_SECTION:
                return "Rewrote section '" + request.getHeading() + "' with no net change.";
            default:
                return "Page body unchanged.";
            }
        }
        String sign = deltaChars > 0 ? "+" : "-";
        int magnitude = Math.abs(deltaChars);
        switch (request.getOperation()) {
        case APPEND:
            return "Appended " + sign + magnitude + " chars to page body.";
        case PREPEND:
            return "Prepended " + sign + magnitude + " chars to page body.";
        case REPLACE_SECTION:
            return "Rewrote section '" + request.getHeading() + "' (" + sign + magnitude + " chars).";
        default:
            return "Patched page (" + sign + magnitude + " chars).";
        }
    }

    public String buildReason(WikiPatchRequest request) {
        return switch (request.getOperation()) {
        case APPEND -> "Patch (append)";
        case PREPEND -> "Patch (prepend)";
        case REPLACE_SECTION -> "Patch (replace section: " + request.getHeading() + ")";
        default -> "Patch";
        };
    }

    private String replaceSection(String body, String heading, String newContent) {
        String[] lines = body.split("\\n", -1);
        int startLine = -1;
        int headingLevel = -1;
        boolean insideFence = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isFenceLine(line)) {
                insideFence = !insideFence;
                continue;
            }
            if (insideFence) {
                continue;
            }
            int level = leadingHashCount(line);
            if (level <= 0) {
                continue;
            }
            String text = line.substring(level).trim();
            if (text.equals(heading)) {
                startLine = i;
                headingLevel = level;
                break;
            }
        }
        if (startLine < 0) {
            throw new IllegalArgumentException("Section heading not found: " + heading);
        }
        int endLine = lines.length;
        boolean scanInsideFence = false;
        for (int j = startLine + 1; j < lines.length; j++) {
            if (isFenceLine(lines[j])) {
                scanInsideFence = !scanInsideFence;
                continue;
            }
            if (scanInsideFence) {
                continue;
            }
            int level = leadingHashCount(lines[j]);
            if (level > 0 && level <= headingLevel) {
                endLine = j;
                break;
            }
        }
        StringBuilder result = new StringBuilder();
        for (int k = 0; k < startLine; k++) {
            result.append(lines[k]);
            if (k < lines.length - 1) {
                result.append('\n');
            }
        }
        result.append(lines[startLine]).append('\n');
        String normalized = newContent;
        if (!normalized.isEmpty() && !normalized.endsWith("\n")) {
            normalized = normalized + "\n";
        }
        result.append(normalized);
        for (int k = endLine; k < lines.length; k++) {
            result.append(lines[k]);
            if (k < lines.length - 1) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    private int leadingHashCount(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '#') {
            count++;
        }
        if (count == 0 || count >= line.length() || line.charAt(count) != ' ') {
            return 0;
        }
        return count;
    }

    private boolean isFenceLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.stripLeading();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }
}
