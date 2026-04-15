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

package me.golemcore.brain.application.port.out;

import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WikiEmbeddingIndexPort {
    void applyChanges(String spaceId, WikiDocumentChangeSet changeSet);

    List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit);

    List<String> listIndexedPaths(String spaceId);

    Map<String, String> listIndexedRevisions(String spaceId);

    int count(String spaceId);

    /**
     * Returns the embedding model id recorded on any stored chunk for the space, or
     * empty if the space has no stored embeddings. Callers treat a mismatch against
     * the currently-configured model as a signal to rebuild the embedding index for
     * that space.
     */
    Optional<String> findStoredEmbeddingModelId(String spaceId);
}
