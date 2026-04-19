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

import lombok.Builder;
import lombok.Value;

/**
 * Search hit shape shared by FTS-only, hybrid fallback, and hybrid vector
 * results.
 *
 * <p>
 * {@code score} is present only when vector or fused ranking contributed a
 * numeric relevance value.
 */
@Value
@Builder
public class WikiSearchResultHit {
    String id;
    String path;
    String title;
    String excerpt;
    String parentPath;
    WikiNodeKind kind;
    Double score;

    /** Converts an FTS hit into the unified API result hit shape. */
    public static WikiSearchResultHit from(WikiSearchHit hit) {
        return WikiSearchResultHit.builder()
                .id(hit.getId())
                .path(hit.getPath())
                .title(hit.getTitle())
                .excerpt(hit.getExcerpt())
                .parentPath(hit.getParentPath())
                .kind(hit.getKind())
                .build();
    }

    /** Converts an embedding or fused hybrid hit into the unified API shape. */
    public static WikiSearchResultHit from(WikiEmbeddingSearchHit hit) {
        return WikiSearchResultHit.builder()
                .id(hit.getId())
                .path(hit.getPath())
                .title(hit.getTitle())
                .excerpt(hit.getExcerpt())
                .parentPath(hit.getParentPath())
                .kind(hit.getKind())
                .score(hit.getScore())
                .build();
    }
}
