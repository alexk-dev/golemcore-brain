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
import me.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import me.golemcore.brain.domain.Secret;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingDocument;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexMetadata;
import me.golemcore.brain.domain.WikiIndexStatus;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiSearchHit;
import me.golemcore.brain.domain.WikiSemanticSearchResult;
import me.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import me.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import me.golemcore.brain.domain.llm.LlmModelConfig;
import me.golemcore.brain.domain.llm.LlmModelKind;
import me.golemcore.brain.domain.llm.LlmProviderConfig;
import me.golemcore.brain.domain.llm.LlmSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WikiIndexingService {

    private static final int SEARCH_LIMIT = 50;
    private static final int EMBEDDING_SEARCH_LIMIT = 10;
    private static final String INDEX_MODE = "lucene+sqlite-embeddings";
    private static final WikiDocumentChunker DEFAULT_CHUNKER = new WikiDocumentChunker(2500, 300);

    private final WikiDocumentCatalogPort wikiDocumentCatalogPort;
    private final WikiFullTextIndexPort wikiFullTextIndexPort;
    private final WikiEmbeddingIndexPort wikiEmbeddingIndexPort;
    private final LlmSettingsRepository llmSettingsRepository;
    private final LlmEmbeddingPort llmEmbeddingPort;
    private final Executor indexingExecutor;
    private final WikiDocumentChunker documentChunker;
    private final Map<String, IndexingObservation> observationsBySpaceId = new LinkedHashMap<>();

    public WikiIndexingService(WikiDocumentCatalogPort wikiDocumentCatalogPort,
            WikiFullTextIndexPort wikiFullTextIndexPort,
            WikiEmbeddingIndexPort wikiEmbeddingIndexPort,
            LlmSettingsRepository llmSettingsRepository,
            LlmEmbeddingPort llmEmbeddingPort,
            Executor indexingExecutor) {
        this(wikiDocumentCatalogPort, wikiFullTextIndexPort, wikiEmbeddingIndexPort, llmSettingsRepository,
                llmEmbeddingPort, indexingExecutor, DEFAULT_CHUNKER);
    }

    /**
     * Reconcile the index against the current catalog state. This is a heavy
     * operation (full catalog scan + optional embedding calls) and MUST NOT be
     * called from the synchronous search path. Invoke via
     * {@link #scheduleSynchronize(String)} or a scheduled reconciliation job.
     */
    public void synchronizeSpace(String spaceId) {
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        List<WikiIndexedDocument> documents = wikiDocumentCatalogPort.listDocuments(spaceId);
        List<String> actualPaths = documents.stream().map(WikiIndexedDocument::getPath).toList();
        Map<String, String> indexedRevisions = wikiFullTextIndexPort.listIndexedRevisions(spaceId);
        List<String> deletedPaths = pathsMissingFrom(new ArrayList<>(indexedRevisions.keySet()), actualPaths);
        boolean embeddingsEnabled = resolvedModel.isPresent();
        boolean embeddingModelChanged = embeddingsEnabled && isStoredEmbeddingModelStale(spaceId, resolvedModel.get());
        Map<String, String> indexedEmbeddingRevisions = embeddingsEnabled && !embeddingModelChanged
                ? wikiEmbeddingIndexPort.listIndexedRevisions(spaceId)
                : Map.of();
        List<WikiIndexedDocument> changedDocuments = documents.stream()
                .filter(document -> embeddingModelChanged
                        || !safeRevision(document).equals(indexedRevisions.get(document.getPath()))
                        || (embeddingsEnabled
                                && !safeRevision(document).equals(indexedEmbeddingRevisions.get(document.getPath()))))
                .toList();
        if (changedDocuments.isEmpty() && deletedPaths.isEmpty()) {
            return;
        }
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(changedDocuments)
                .embeddingUpserts(List.of())
                .deletedPaths(deletedPaths)
                .fullRebuild(embeddingModelChanged)
                .build(), resolvedModel);
    }

    private boolean isStoredEmbeddingModelStale(String spaceId, ResolvedEmbeddingModel resolvedModel) {
        Optional<String> storedModelId = wikiEmbeddingIndexPort.findStoredEmbeddingModelId(spaceId);
        return storedModelId.isPresent() && !storedModelId.get().equals(resolvedModel.model().getId());
    }

    /**
     * Dispatches a full reconciliation of the given space onto the indexing
     * executor. Production wires a single-thread executor, so reconciliations are
     * serialized across spaces — one slow sync blocks the queue, but exceptions in
     * one task never kill the worker thread (they are caught and recorded as
     * indexing failures for the affected space).
     */
    public void scheduleSynchronize(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            return;
        }
        indexingExecutor.execute(() -> {
            try {
                synchronizeSpace(spaceId);
            } catch (RuntimeException exception) {
                // synchronizeSpace already records the failure via applyChanges;
                // swallowing here prevents an uncaught exception from killing the
                // single-thread indexing executor and losing subsequent passes.
                recordIndexingFailure(spaceId, exception);
            }
        });
    }

    public void recordUpsert(String spaceId, WikiIndexedDocument document) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(List.of(document))
                .embeddingUpserts(List.of())
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build(), resolveEmbeddingModel());
    }

    public void recordUpserts(String spaceId, List<WikiIndexedDocument> documents) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(documents == null ? List.of() : documents)
                .embeddingUpserts(List.of())
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build(), resolveEmbeddingModel());
    }

    public void recordDeletes(String spaceId, List<String> deletedPaths) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(List.of())
                .embeddingUpserts(List.of())
                .deletedPaths(deletedPaths == null ? List.of() : deletedPaths)
                .fullRebuild(false)
                .build(), resolveEmbeddingModel());
    }

    public void rebuildSpace(String spaceId) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(wikiDocumentCatalogPort.listDocuments(spaceId))
                .embeddingUpserts(List.of())
                .deletedPaths(List.of())
                .fullRebuild(true)
                .build(), resolveEmbeddingModel());
        observationsBySpaceId.computeIfAbsent(spaceId,
                key -> IndexingObservation.builder().build()).lastFullRebuildAt = Instant.now();
    }

    public List<WikiSearchHit> search(String spaceId, String query) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return wikiFullTextIndexPort.search(spaceId, normalizedQuery, SEARCH_LIMIT);
    }

    public List<WikiEmbeddingSearchHit> embeddingSearch(String spaceId, String query) {
        return embeddingSearch(spaceId, query, EMBEDDING_SEARCH_LIMIT);
    }

    public List<WikiEmbeddingSearchHit> embeddingSearch(String spaceId, String query, int limit) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        if (resolvedModel.isEmpty()) {
            return List.of();
        }
        Optional<List<Double>> queryEmbedding = embedOne(resolvedModel.get(), normalizedQuery);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        int effectiveLimit = Math.max(1, limit);
        // Overfetch leaves headroom for a future reranker without changing the
        // API — the caller sees only its requested top-k.
        List<WikiEmbeddingSearchHit> hits = wikiEmbeddingIndexPort.search(spaceId, queryEmbedding.get(),
                overfetch(effectiveLimit));
        return hits.size() <= effectiveLimit ? hits : hits.subList(0, effectiveLimit);
    }

    public WikiSemanticSearchResult semanticSearch(String spaceId, String query) {
        return semanticSearch(spaceId, query, EMBEDDING_SEARCH_LIMIT);
    }

    public WikiSemanticSearchResult semanticSearch(String spaceId, String query, int limit) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return WikiSemanticSearchResult.builder()
                    .mode("empty-query")
                    .semanticReady(false)
                    .semanticHits(List.of())
                    .fallbackHits(List.of())
                    .build();
        }
        int effectiveLimit = Math.max(1, limit);
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        if (resolvedModel.isEmpty()) {
            return fallbackSemanticResult(spaceId, normalizedQuery, "embedding-model-not-configured");
        }
        Optional<List<Double>> queryEmbedding = embedOne(resolvedModel.get(), normalizedQuery);
        if (queryEmbedding.isEmpty()) {
            return fallbackSemanticResult(spaceId, normalizedQuery, "embedding-query-failed");
        }
        // Hybrid retrieval: fetch dense and lexical top-k, then fuse with
        // Reciprocal Rank Fusion. RRF is scale-free, has one near-universal
        // hyperparameter (k=60), and recovers both "exact name" wins from BM25
        // and "paraphrase" wins from the embedding model without tuning.
        int overfetched = overfetch(effectiveLimit);
        List<WikiEmbeddingSearchHit> denseHits = wikiEmbeddingIndexPort.search(spaceId, queryEmbedding.get(),
                overfetched);
        List<WikiSearchHit> lexicalHits = wikiFullTextIndexPort.search(spaceId, normalizedQuery, overfetched);
        if (denseHits.isEmpty() && lexicalHits.isEmpty()) {
            return fallbackSemanticResult(spaceId, normalizedQuery, "embedding-index-empty");
        }
        List<WikiEmbeddingSearchHit> fused = fuseReciprocalRank(denseHits, lexicalHits, effectiveLimit);
        return WikiSemanticSearchResult.builder()
                .mode("hybrid")
                .semanticReady(true)
                .semanticHits(fused)
                .fallbackHits(List.of())
                .build();
    }

    private int overfetch(int limit) {
        return Math.max(limit * 2, EMBEDDING_SEARCH_LIMIT);
    }

    private static final int RRF_K = 60;

    private List<WikiEmbeddingSearchHit> fuseReciprocalRank(List<WikiEmbeddingSearchHit> denseHits,
            List<WikiSearchHit> lexicalHits, int limit) {
        LinkedHashMap<String, FusedHit> byPath = new LinkedHashMap<>();
        int rank = 0;
        for (WikiEmbeddingSearchHit hit : denseHits) {
            rank++;
            FusedHit fused = byPath.computeIfAbsent(hit.getPath(), path -> new FusedHit(hit));
            fused.accumulate(1.0d / (RRF_K + rank));
        }
        rank = 0;
        for (WikiSearchHit hit : lexicalHits) {
            rank++;
            FusedHit fused = byPath.computeIfAbsent(hit.getPath(), path -> new FusedHit(hit));
            fused.accumulate(1.0d / (RRF_K + rank));
        }
        return byPath.values().stream()
                .sorted(Comparator.comparingDouble(FusedHit::score).reversed())
                .limit(Math.max(1, limit))
                .map(FusedHit::asEmbeddingHit)
                .toList();
    }

    private static final class FusedHit {
        private final WikiEmbeddingSearchHit denseHit;
        private final WikiSearchHit lexicalHit;
        private double score;

        private FusedHit(WikiEmbeddingSearchHit denseHit) {
            this.denseHit = denseHit;
            this.lexicalHit = null;
        }

        private FusedHit(WikiSearchHit lexicalHit) {
            this.denseHit = null;
            this.lexicalHit = lexicalHit;
        }

        private void accumulate(double contribution) {
            this.score += contribution;
        }

        private double score() {
            return score;
        }

        private WikiEmbeddingSearchHit asEmbeddingHit() {
            if (denseHit != null) {
                return WikiEmbeddingSearchHit.builder()
                        .id(denseHit.getId())
                        .path(denseHit.getPath())
                        .title(denseHit.getTitle())
                        .excerpt(denseHit.getExcerpt())
                        .parentPath(denseHit.getParentPath())
                        .kind(denseHit.getKind())
                        .score(score)
                        .build();
            }
            return WikiEmbeddingSearchHit.builder()
                    .id(lexicalHit.getPath())
                    .path(lexicalHit.getPath())
                    .title(lexicalHit.getTitle())
                    .excerpt(lexicalHit.getExcerpt())
                    .parentPath(lexicalHit.getParentPath())
                    .kind(lexicalHit.getKind())
                    .score(score)
                    .build();
        }
    }

    public WikiIndexStatus getStatus(String spaceId) {
        WikiIndexMetadata metadata = metadata(spaceId);
        return WikiIndexStatus.builder()
                .mode(INDEX_MODE)
                .ready(metadata.isReady())
                .totalDocuments(metadata.getTotalDocuments())
                .fullTextIndexedDocuments(metadata.getFullTextIndexedDocuments())
                .embeddingIndexedDocuments(metadata.getEmbeddingIndexedDocuments())
                .staleDocuments(metadata.getStaleDocuments())
                .lastUpdatedAt(metadata.getLastUpdatedAt())
                .embeddingsReady(metadata.isEmbeddingsReady())
                .lastIndexingError(metadata.getLastIndexingError())
                .embeddingModelId(metadata.getEmbeddingModelId())
                .lastFullRebuildAt(metadata.getLastFullRebuildAt())
                .build();
    }

    private WikiSemanticSearchResult fallbackSemanticResult(String spaceId, String query, String reason) {
        return WikiSemanticSearchResult.builder()
                .mode("lexical-fallback")
                .semanticReady(false)
                .fallbackReason(reason)
                .semanticHits(List.of())
                .fallbackHits(search(spaceId, query))
                .build();
    }

    private void applyChanges(WikiDocumentChangeSet changeSet, Optional<ResolvedEmbeddingModel> resolvedModel) {
        if (changeSet == null || changeSet.isEmpty()) {
            return;
        }
        WikiDocumentChangeSet normalizedChangeSet = normalizeChangeSet(changeSet);
        try {
            wikiFullTextIndexPort.applyChanges(normalizedChangeSet.getSpaceId(), normalizedChangeSet);
            wikiEmbeddingIndexPort.applyChanges(normalizedChangeSet.getSpaceId(),
                    withEmbeddings(normalizedChangeSet, resolvedModel));
            recordIndexingSuccess(normalizedChangeSet.getSpaceId());
        } catch (RuntimeException exception) {
            recordIndexingFailure(normalizedChangeSet.getSpaceId(), exception);
            throw new IllegalStateException("Failed to update search indexes", exception);
        }
    }

    private WikiDocumentChangeSet normalizeChangeSet(WikiDocumentChangeSet changeSet) {
        List<WikiIndexedDocument> upserts = changeSet.getUpserts() == null ? List.of() : changeSet.getUpserts();
        List<WikiEmbeddingDocument> embeddingUpserts = changeSet.getEmbeddingUpserts() == null
                ? List.of()
                : changeSet.getEmbeddingUpserts();
        List<String> deletedPaths = changeSet.getDeletedPaths() == null ? List.of() : changeSet.getDeletedPaths();
        return WikiDocumentChangeSet.builder()
                .spaceId(changeSet.getSpaceId())
                .upserts(upserts)
                .embeddingUpserts(embeddingUpserts)
                .deletedPaths(deletedPaths)
                .fullRebuild(changeSet.isFullRebuild())
                .build();
    }

    private WikiDocumentChangeSet withEmbeddings(WikiDocumentChangeSet changeSet,
            Optional<ResolvedEmbeddingModel> resolvedModel) {
        if (changeSet.getUpserts().isEmpty() || resolvedModel.isEmpty()) {
            return WikiDocumentChangeSet.builder()
                    .spaceId(changeSet.getSpaceId())
                    .upserts(changeSet.getUpserts())
                    .embeddingUpserts(List.of())
                    .deletedPaths(changeSet.getDeletedPaths())
                    .fullRebuild(changeSet.isFullRebuild())
                    .build();
        }
        return WikiDocumentChangeSet.builder()
                .spaceId(changeSet.getSpaceId())
                .upserts(changeSet.getUpserts())
                .embeddingUpserts(embedDocuments(changeSet.getSpaceId(), resolvedModel.get(), changeSet.getUpserts()))
                .deletedPaths(changeSet.getDeletedPaths())
                .fullRebuild(changeSet.isFullRebuild())
                .build();
    }

    private List<WikiEmbeddingDocument> embedDocuments(String spaceId, ResolvedEmbeddingModel resolvedModel,
            List<WikiIndexedDocument> documents) {
        if (documents.isEmpty()) {
            return List.of();
        }
        List<ChunkedInput> chunkedInputs = new ArrayList<>();
        for (WikiIndexedDocument document : documents) {
            List<WikiDocumentChunker.Chunk> chunks = documentChunker.chunk(document.getBody());
            for (WikiDocumentChunker.Chunk chunk : chunks) {
                chunkedInputs.add(new ChunkedInput(document, chunk));
            }
        }
        if (chunkedInputs.isEmpty()) {
            return List.of();
        }
        // Every chunk embeds the document title as a topical prefix so a
        // mid-body chunk that never mentions the subject still retrieves on
        // short queries like "pricing" or "onboarding" — the stored chunkText
        // remains the raw body slice to keep excerpts clean.
        List<String> inputs = chunkedInputs.stream().map(WikiIndexingService::buildEmbedInput).toList();
        LlmEmbeddingResponse response;
        try {
            response = llmEmbeddingPort.embed(LlmEmbeddingRequest.builder()
                    .provider(resolvedModel.provider())
                    .model(resolvedModel.model())
                    .inputs(inputs)
                    .build());
        } catch (RuntimeException exception) {
            recordIndexingFailure(spaceId, exception);
            return List.of();
        }
        if (response == null || response.getEmbeddings() == null) {
            return List.of();
        }
        // Pair by index and keep whatever vectors came back. A short response
        // means some chunks lack embeddings this pass; they'll be picked up on
        // the next reconciliation rather than losing all progress for the doc.
        int pairable = Math.min(response.getEmbeddings().size(), chunkedInputs.size());
        String modelId = resolvedModel.model().getId();
        List<WikiEmbeddingDocument> embeddingDocuments = new ArrayList<>();
        for (int index = 0; index < pairable; index++) {
            List<Double> vector = response.getEmbeddings().get(index);
            if (vector == null || vector.isEmpty()) {
                continue;
            }
            ChunkedInput entry = chunkedInputs.get(index);
            embeddingDocuments.add(WikiEmbeddingDocument.builder()
                    .document(entry.document())
                    .chunkIndex(entry.chunk().index())
                    .chunkText(entry.chunk().text())
                    .embeddingModelId(modelId)
                    .vector(vector)
                    .build());
        }
        return embeddingDocuments;
    }

    private record ChunkedInput(WikiIndexedDocument document, WikiDocumentChunker.Chunk chunk) {
    }

    private static String buildEmbedInput(ChunkedInput entry) {
        String title = entry.document().getTitle() == null ? "" : entry.document().getTitle().strip();
        String text = entry.chunk().text() == null ? "" : entry.chunk().text();
        if (title.isEmpty()) {
            return text;
        }
        return title + "\n\n" + text;
    }

    private Optional<List<Double>> embedOne(ResolvedEmbeddingModel resolvedModel, String text) {
        LlmEmbeddingResponse response;
        try {
            response = llmEmbeddingPort.embed(LlmEmbeddingRequest.builder()
                    .provider(resolvedModel.provider())
                    .model(resolvedModel.model())
                    .inputs(List.of(text))
                    .build());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            return Optional.empty();
        }
        List<Double> embedding = response.getEmbeddings().getFirst();
        return embedding == null || embedding.isEmpty() ? Optional.empty() : Optional.of(embedding);
    }

    private Optional<ResolvedEmbeddingModel> resolveEmbeddingModel() {
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        return settings.getModels().stream()
                .filter(model -> model.getKind() == LlmModelKind.EMBEDDING)
                .filter(model -> !Boolean.FALSE.equals(model.getEnabled()))
                .sorted(Comparator.comparing(model -> Optional.ofNullable(model.getDisplayName())
                        .orElse(model.getModelId()), String.CASE_INSENSITIVE_ORDER))
                .map(model -> toResolvedModel(settings, model))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Optional<ResolvedEmbeddingModel> toResolvedModel(LlmSettings settings, LlmModelConfig model) {
        LlmProviderConfig provider = settings.getProviders().get(model.getProvider());
        if (provider == null || !Secret.hasValue(provider.getApiKey())) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedEmbeddingModel(provider, model));
    }

    private LlmSettings normalizeSettings(LlmSettings settings) {
        if (settings == null) {
            return LlmSettings.builder().build();
        }
        if (settings.getProviders() == null) {
            settings.setProviders(new LinkedHashMap<>());
        }
        if (settings.getModels() == null) {
            settings.setModels(new ArrayList<>());
        }
        return settings;
    }

    private WikiIndexMetadata metadata(String spaceId) {
        List<WikiIndexedDocument> documents = wikiDocumentCatalogPort.listDocuments(spaceId);
        Instant lastUpdatedAt = documents.stream()
                .map(WikiIndexedDocument::getUpdatedAt)
                .max(Instant::compareTo)
                .orElse(Instant.now());
        int fullTextCount = wikiFullTextIndexPort.count(spaceId);
        int embeddingCount = wikiEmbeddingIndexPort.count(spaceId);
        int staleDocuments = Math.max(0, documents.size() - fullTextCount);
        IndexingObservation observation = observationsBySpaceId.getOrDefault(spaceId,
                IndexingObservation.builder().build());
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        String configuredModelId = resolvedModel.map(ResolvedEmbeddingModel::model)
                .map(LlmModelConfig::getId)
                .orElse(null);
        Optional<String> storedModelId = wikiEmbeddingIndexPort.findStoredEmbeddingModelId(spaceId);
        boolean modelMatches = configuredModelId != null && storedModelId.map(configuredModelId::equals).orElse(false);
        return WikiIndexMetadata.builder()
                .totalDocuments(documents.size())
                .fullTextIndexedDocuments(fullTextCount)
                .embeddingIndexedDocuments(embeddingCount)
                .staleDocuments(staleDocuments)
                .lastUpdatedAt(lastUpdatedAt)
                .lastIndexingError(observation.lastIndexingError)
                .embeddingModelId(configuredModelId)
                .lastFullRebuildAt(observation.lastFullRebuildAt)
                .ready(staleDocuments == 0)
                .embeddingsReady(embeddingCount > 0 && modelMatches)
                .build();
    }

    private void recordIndexingSuccess(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            return;
        }
        observationsBySpaceId.remove(spaceId);
    }

    private void recordIndexingFailure(String spaceId, RuntimeException exception) {
        if (spaceId == null || spaceId.isBlank()) {
            return;
        }
        observationsBySpaceId.computeIfAbsent(spaceId,
                key -> IndexingObservation.builder().build()).lastIndexingError = exception.getMessage();
    }

    private List<String> pathsMissingFrom(List<String> existingPaths, List<String> actualPaths) {
        Set<String> actual = new LinkedHashSet<>(actualPaths == null ? List.of() : actualPaths);
        return (existingPaths == null ? List.<String>of() : existingPaths).stream()
                .filter(path -> !actual.contains(path))
                .toList();
    }

    private String safeRevision(WikiIndexedDocument document) {
        return document.getRevision() == null ? "" : document.getRevision();
    }

    @lombok.Builder
    private static class IndexingObservation {
        private String lastIndexingError;
        private Instant lastFullRebuildAt;
    }

    private record ResolvedEmbeddingModel(LlmProviderConfig provider, LlmModelConfig model) {
    }
}
