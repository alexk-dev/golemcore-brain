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

package me.golemcore.brain.domain;

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
