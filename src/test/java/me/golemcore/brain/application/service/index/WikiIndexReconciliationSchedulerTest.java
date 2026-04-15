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

package me.golemcore.brain.application.service.index;

import me.golemcore.brain.application.port.out.LlmEmbeddingPort;
import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiSearchHit;
import me.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import me.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import me.golemcore.brain.domain.llm.LlmSettings;
import me.golemcore.brain.domain.space.Space;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiIndexReconciliationSchedulerTest {

    @Test
    void shouldScheduleSynchronizeForEveryKnownSpace() {
        RecordingSpaceRepository spaceRepository = new RecordingSpaceRepository();
        spaceRepository.spaces = List.of(
                Space.builder().id("space-a").slug("alpha").build(),
                Space.builder().id("space-b").slug("beta").build());
        RecordingIndexingService indexingService = new RecordingIndexingService();
        WikiIndexReconciliationScheduler scheduler = new WikiIndexReconciliationScheduler(spaceRepository,
                indexingService);

        scheduler.reconcileAll();

        assertEquals(List.of("space-a", "space-b"), indexingService.scheduledSpaceIds);
    }

    @Test
    void shouldContinueReconcilingWhenOneSpaceFailsAtStartup() {
        // Startup reconciliation must not abort when one space is broken —
        // otherwise a single bad space makes every other space unsearchable
        // until the failing one is fixed.
        RecordingSpaceRepository spaceRepository = new RecordingSpaceRepository();
        spaceRepository.spaces = List.of(
                Space.builder().id("space-a").slug("alpha").build(),
                Space.builder().id("space-broken").slug("broken").build(),
                Space.builder().id("space-c").slug("gamma").build());
        RecordingIndexingService indexingService = new RecordingIndexingService();
        indexingService.failingSpaceId = "space-broken";
        WikiIndexReconciliationScheduler scheduler = new WikiIndexReconciliationScheduler(spaceRepository,
                indexingService);

        scheduler.reconcileAllNow();

        assertEquals(List.of("space-a", "space-broken", "space-c"), indexingService.synchronizedSpaceIds);
    }

    private static class RecordingIndexingService extends WikiIndexingService {
        private final List<String> scheduledSpaceIds = new ArrayList<>();
        private final List<String> synchronizedSpaceIds = new ArrayList<>();
        private String failingSpaceId;

        RecordingIndexingService() {
            super(
                    new NoopDocumentCatalog(),
                    new NoopFullTextIndex(),
                    new NoopEmbeddingIndex(),
                    new StaticLlmSettingsRepository(),
                    new NoopEmbeddingPort(),
                    Runnable::run);
        }

        @Override
        public void scheduleSynchronize(String spaceId) {
            scheduledSpaceIds.add(spaceId);
        }

        @Override
        public void synchronizeSpace(String spaceId) {
            synchronizedSpaceIds.add(spaceId);
            if (spaceId.equals(failingSpaceId)) {
                throw new IllegalStateException("simulated failure for " + spaceId);
            }
        }
    }

    private static class RecordingSpaceRepository implements SpaceRepository {
        private List<Space> spaces = List.of();

        @Override
        public void initialize() {
        }

        @Override
        public List<Space> listSpaces() {
            return spaces;
        }

        @Override
        public Optional<Space> findById(String id) {
            return spaces.stream().filter(space -> space.getId().equals(id)).findFirst();
        }

        @Override
        public Optional<Space> findBySlug(String slug) {
            return spaces.stream().filter(space -> space.getSlug().equals(slug)).findFirst();
        }

        @Override
        public Space save(Space space) {
            return space;
        }

        @Override
        public void delete(String id) {
        }
    }

    private static class NoopDocumentCatalog implements WikiDocumentCatalogPort {
        @Override
        public List<WikiIndexedDocument> listDocuments(String spaceId) {
            return List.of();
        }
    }

    private static class NoopFullTextIndex implements WikiFullTextIndexPort {
        @Override
        public void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
        }

        @Override
        public List<WikiSearchHit> search(String spaceId, String query, int limit) {
            return List.of();
        }

        @Override
        public List<String> listIndexedPaths(String spaceId) {
            return List.of();
        }

        @Override
        public Map<String, String> listIndexedRevisions(String spaceId) {
            return Map.of();
        }

        @Override
        public int count(String spaceId) {
            return 0;
        }
    }

    private static class NoopEmbeddingIndex implements WikiEmbeddingIndexPort {
        @Override
        public void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
        }

        @Override
        public List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit) {
            return List.of();
        }

        @Override
        public List<String> listIndexedPaths(String spaceId) {
            return List.of();
        }

        @Override
        public Map<String, String> listIndexedRevisions(String spaceId) {
            return Map.of();
        }

        @Override
        public int count(String spaceId) {
            return 0;
        }

        @Override
        public Optional<String> findStoredEmbeddingModelId(String spaceId) {
            return Optional.empty();
        }
    }

    private static class StaticLlmSettingsRepository implements LlmSettingsRepository {
        @Override
        public void initialize() {
        }

        @Override
        public LlmSettings load() {
            return LlmSettings.builder().build();
        }

        @Override
        public LlmSettings save(LlmSettings settings) {
            return settings;
        }
    }

    private static class NoopEmbeddingPort implements LlmEmbeddingPort {
        @Override
        public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
            return LlmEmbeddingResponse.builder().embeddings(List.of()).build();
        }
    }
}
