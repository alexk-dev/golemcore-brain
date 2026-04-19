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

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Unified response returned by {@code POST /api/spaces/{slug}/search}.
 *
 * <p>
 * {@code mode} reports the effective retrieval path actually used, not merely
 * the requested search preference from the request payload. Current values are:
 *
 * <ul>
 * <li>{@code fts} when the caller explicitly requested full-text search</li>
 * <li>{@code hybrid} when vector plus full-text fusion participated in
 * ranking</li>
 * <li>{@code fts-fallback} when {@code auto} or {@code hybrid} was requested
 * but embeddings could not participate</li>
 * <li>{@code empty-query} when the request query is blank after trimming</li>
 * </ul>
 *
 * <p>
 * {@code semanticReady} tells clients whether vector retrieval actually
 * participated in ranking for this response. When vector retrieval cannot run,
 * {@code fallbackReason} explains why the result was produced by FTS only.
 */
@Value
@Builder
public class WikiSearchResult {
    String mode;
    boolean semanticReady;
    String fallbackReason;
    List<WikiSearchResultHit> hits;
}
