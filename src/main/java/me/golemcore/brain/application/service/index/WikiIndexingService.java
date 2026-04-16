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
import me.golemcore.brain.domain.WikiSearchMode;
import me.golemcore.brain.domain.WikiSearchResult;
import me.golemcore.brain.domain.WikiSearchResultHit;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private final ConcurrentMap<String, IndexingObservation> observationsBySpaceId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> embeddingUnavailableReasonsBySpaceId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledRebuildState> scheduledRebuildsBySpaceId = new ConcurrentHashMap<>();

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
        List<String> fullTextDeletedPaths = pathsMissingFrom(new ArrayList<>(indexedRevisions.keySet()), actualPaths);
        boolean embeddingsEnabled = resolvedModel.isPresent();
        boolean embeddingModelChanged = embeddingsEnabled && isStoredEmbeddingModelStale(spaceId, resolvedModel.get());
        Map<String, String> indexedEmbeddingRevisions = embeddingsEnabled && !embeddingModelChanged
                ? wikiEmbeddingIndexPort.listIndexedRevisions(spaceId)
                : Map.of();
        List<String> embeddingDeletedPaths = embeddingsEnabled && !embeddingModelChanged
                ? pathsMissingFrom(new ArrayList<>(indexedEmbeddingRevisions.keySet()), actualPaths)
                : List.of();
        List<String> deletedPaths = mergeDistinct(fullTextDeletedPaths, embeddingDeletedPaths);
        List<WikiIndexedDocument> changedDocuments = documents.stream()
                .filter(document -> embeddingModelChanged
                        || !safeRevision(document).equals(indexedRevisions.get(document.getPath()))
                        || (embeddingsEnabled
                                && !safeRevision(document).equals(indexedEmbeddingRevisions.get(document.getPath()))))
                .toList();
        if (changedDocuments.isEmpty() && deletedPaths.isEmpty() && !embeddingModelChanged) {
            clearEmbeddingUnavailableReason(spaceId);
            return;
        }
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(changedDocuments)
                .embeddingUpserts(List.of())
                .deletedPaths(deletedPaths)
                .fullRebuild(embeddingModelChanged)
                .build(), resolvedModel, true);
    }

    private boolean isStoredEmbeddingModelStale(String spaceId, ResolvedEmbeddingModel resolvedModel) {
        Optional<String> storedModelId = wikiEmbeddingIndexPort.findStoredEmbeddingModelId(spaceId);
        if (storedModelId.isPresent()) {
            return !storedModelId.get().equals(resolvedModel.model().getId());
        }
        return wikiEmbeddingIndexPort.count(spaceId) > 0;
    }

    private Optional<String> embeddingIndexUnavailableReason(String spaceId, ResolvedEmbeddingModel resolvedModel) {
        if (isStoredEmbeddingModelStale(spaceId, resolvedModel)) {
            return Optional.of("embedding-model-mismatch");
        }
        String recordedReason = embeddingUnavailableReasonsBySpaceId.get(spaceId);
        if (recordedReason != null && !recordedReason.isBlank()) {
            return Optional.of(recordedReason);
        }
        int embeddingCount = wikiEmbeddingIndexPort.count(spaceId);
        if (embeddingCount == 0) {
            return Optional.of("embedding-index-empty");
        }
        if (wikiFullTextIndexPort.count(spaceId) != embeddingCount) {
            return Optional.of("embedding-index-incomplete");
        }
        return Optional.empty();
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

    public void scheduleRebuild(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            return;
        }
        ScheduledRebuildState state = reserveScheduledRebuild(spaceId);
        if (state != null) {
            dispatchScheduledRebuild(spaceId, state);
        }
    }

    private ScheduledRebuildState reserveScheduledRebuild(String spaceId) {
        while (true) {
            ScheduledRebuildState state = scheduledRebuildsBySpaceId.computeIfAbsent(spaceId,
                    key -> new ScheduledRebuildState());
            synchronized (state) {
                if (state.removed) {
                    continue;
                }
                if (state.running) {
                    state.rerunRequested = true;
                    return null;
                }
                if (state.queued) {
                    return null;
                }
                state.queued = true;
                return state;
            }
        }
    }

    private void dispatchScheduledRebuild(String spaceId, ScheduledRebuildState state) {
        try {
            indexingExecutor.execute(() -> runScheduledRebuild(spaceId, state));
        } catch (RuntimeException exception) {
            cancelScheduledRebuild(spaceId, state);
            throw exception;
        }
    }

    private void runScheduledRebuild(String spaceId, ScheduledRebuildState state) {
        synchronized (state) {
            state.queued = false;
            state.running = true;
        }
        try {
            rebuildSpace(spaceId);
        } catch (RuntimeException exception) {
            recordIndexingFailure(spaceId, exception);
        } finally {
            completeScheduledRebuild(spaceId, state);
        }
    }

    private void completeScheduledRebuild(String spaceId, ScheduledRebuildState state) {
        boolean dispatchAgain;
        synchronized (state) {
            state.running = false;
            dispatchAgain = state.rerunRequested;
            state.rerunRequested = false;
            if (dispatchAgain) {
                state.queued = true;
            } else {
                state.removed = true;
                scheduledRebuildsBySpaceId.remove(spaceId, state);
            }
        }
        if (dispatchAgain) {
            dispatchScheduledRebuild(spaceId, state);
        }
    }

    private void cancelScheduledRebuild(String spaceId, ScheduledRebuildState state) {
        synchronized (state) {
            state.queued = false;
            state.running = false;
            state.rerunRequested = false;
            state.removed = true;
            scheduledRebuildsBySpaceId.remove(spaceId, state);
        }
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
                .build(), resolveEmbeddingModel(), true);
        observationsBySpaceId.computeIfAbsent(spaceId,
                key -> IndexingObservation.builder().build()).lastFullRebuildAt = Instant.now();
    }

    public List<WikiSearchHit> search(String spaceId, String query) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return searchFullText(spaceId, normalizedQuery, SEARCH_LIMIT);
    }

    public WikiSearchResult search(String spaceId, String query, WikiSearchMode mode, int limit) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return WikiSearchResult.builder()
                    .mode("empty-query")
                    .semanticReady(false)
                    .hits(List.of())
                    .build();
        }
        int effectiveLimit = Math.max(1, limit);
        WikiSearchMode effectiveMode = Optional.ofNullable(mode).orElse(WikiSearchMode.AUTO);
        if (WikiSearchMode.FTS.equals(effectiveMode)) {
            return WikiSearchResult.builder()
                    .mode("fts")
                    .semanticReady(false)
                    .hits(searchFullText(spaceId, normalizedQuery, effectiveLimit).stream()
                            .map(WikiSearchResultHit::from)
                            .toList())
                    .build();
        }
        return hybridSearch(spaceId, normalizedQuery, effectiveLimit);
    }

    private List<WikiSearchHit> searchFullText(String spaceId, String normalizedQuery, int limit) {
        return wikiFullTextIndexPort.search(spaceId, normalizedQuery, Math.max(1, limit));
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
        if (embeddingIndexUnavailableReason(spaceId, resolvedModel.get()).isPresent()) {
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

    private WikiSearchResult hybridSearch(String spaceId, String query, int limit) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return WikiSearchResult.builder()
                    .mode("empty-query")
                    .semanticReady(false)
                    .hits(List.of())
                    .build();
        }
        int effectiveLimit = Math.max(1, limit);
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        if (resolvedModel.isEmpty()) {
            return fallbackHybridResult(spaceId, normalizedQuery, "embedding-model-not-configured", effectiveLimit);
        }
        Optional<String> unavailableReason = embeddingIndexUnavailableReason(spaceId, resolvedModel.get());
        if (unavailableReason.isPresent()) {
            return fallbackHybridResult(spaceId, normalizedQuery, unavailableReason.get(), effectiveLimit);
        }
        Optional<List<Double>> queryEmbedding = embedOne(resolvedModel.get(), normalizedQuery);
        if (queryEmbedding.isEmpty()) {
            return fallbackHybridResult(spaceId, normalizedQuery, "embedding-query-failed", effectiveLimit);
        }
        // Hybrid retrieval: fetch dense and FTS top-k, then fuse with
        // Reciprocal Rank Fusion. RRF is scale-free, has one near-universal
        // hyperparameter (k=60), and recovers both "exact name" wins from BM25
        // and "paraphrase" wins from the embedding model without tuning.
        int overfetched = overfetch(effectiveLimit);
        List<WikiEmbeddingSearchHit> denseHits = wikiEmbeddingIndexPort.search(spaceId, queryEmbedding.get(),
                overfetched);
        List<WikiSearchHit> ftsHits = wikiFullTextIndexPort.search(spaceId, normalizedQuery, overfetched);
        if (denseHits.isEmpty() && ftsHits.isEmpty()) {
            return fallbackHybridResult(spaceId, normalizedQuery, "embedding-index-empty", effectiveLimit);
        }
        List<WikiEmbeddingSearchHit> fused = fuseReciprocalRank(denseHits, ftsHits, effectiveLimit);
        return WikiSearchResult.builder()
                .mode("hybrid")
                .semanticReady(true)
                .hits(fused.stream()
                        .map(WikiSearchResultHit::from)
                        .toList())
                .build();
    }

    private int overfetch(int limit) {
        return Math.max(limit * 2, EMBEDDING_SEARCH_LIMIT);
    }

    private static final int RRF_K = 60;

    private List<WikiEmbeddingSearchHit> fuseReciprocalRank(List<WikiEmbeddingSearchHit> denseHits,
            List<WikiSearchHit> ftsHits, int limit) {
        Map<String, FusedHit> byPath = new LinkedHashMap<>();
        int rank = 0;
        for (WikiEmbeddingSearchHit hit : denseHits) {
            rank++;
            FusedHit fused = byPath.computeIfAbsent(hit.getPath(), path -> new FusedHit(hit));
            fused.accumulate(1.0d / (RRF_K + rank));
        }
        rank = 0;
        for (WikiSearchHit hit : ftsHits) {
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
        private final WikiSearchHit ftsHit;
        private double rrfScore;

        private FusedHit(WikiEmbeddingSearchHit denseHit) {
            this.denseHit = denseHit;
            this.ftsHit = null;
        }

        private FusedHit(WikiSearchHit ftsHit) {
            this.denseHit = null;
            this.ftsHit = ftsHit;
        }

        private void accumulate(double contribution) {
            this.rrfScore += contribution;
        }

        private double score() {
            return rrfScore;
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
                        .score(rrfScore)
                        .build();
            }
            return WikiEmbeddingSearchHit.builder()
                    .id(ftsHit.getPath())
                    .path(ftsHit.getPath())
                    .title(ftsHit.getTitle())
                    .excerpt(ftsHit.getExcerpt())
                    .parentPath(ftsHit.getParentPath())
                    .kind(ftsHit.getKind())
                    .score(rrfScore)
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

    private WikiSearchResult fallbackHybridResult(String spaceId, String query, String reason, int limit) {
        return WikiSearchResult.builder()
                .mode("fts-fallback")
                .semanticReady(false)
                .fallbackReason(reason)
                .hits(searchFullText(spaceId, query, limit).stream()
                        .map(WikiSearchResultHit::from)
                        .toList())
                .build();
    }

    private void applyChanges(WikiDocumentChangeSet changeSet, Optional<ResolvedEmbeddingModel> resolvedModel) {
        applyChanges(changeSet, resolvedModel, false);
    }

    private void applyChanges(WikiDocumentChangeSet changeSet, Optional<ResolvedEmbeddingModel> resolvedModel,
            boolean authoritativeReconciliation) {
        if (changeSet == null || changeSet.isEmpty()) {
            return;
        }
        WikiDocumentChangeSet normalizedChangeSet = normalizeChangeSet(changeSet);
        try {
            wikiFullTextIndexPort.applyChanges(normalizedChangeSet.getSpaceId(), normalizedChangeSet);
            PreparedEmbeddingChangeSet preparedEmbeddings = withEmbeddings(normalizedChangeSet, resolvedModel);
            wikiEmbeddingIndexPort.applyChanges(normalizedChangeSet.getSpaceId(), preparedEmbeddings.changeSet());
            recordEmbeddingReadiness(normalizedChangeSet.getSpaceId(), preparedEmbeddings,
                    authoritativeReconciliation || normalizedChangeSet.isFullRebuild());
            if (!preparedEmbeddings.indexingFailed()) {
                recordIndexingSuccess(normalizedChangeSet.getSpaceId());
            }
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

    private PreparedEmbeddingChangeSet withEmbeddings(WikiDocumentChangeSet changeSet,
            Optional<ResolvedEmbeddingModel> resolvedModel) {
        boolean staleExistingModel = resolvedModel.isPresent() && !changeSet.isFullRebuild()
                && isStoredEmbeddingModelStale(changeSet.getSpaceId(), resolvedModel.get());
        if (changeSet.getUpserts().isEmpty() || resolvedModel.isEmpty() || staleExistingModel) {
            WikiDocumentChangeSet preparedChangeSet = WikiDocumentChangeSet.builder()
                    .spaceId(changeSet.getSpaceId())
                    .upserts(changeSet.getUpserts())
                    .embeddingUpserts(List.of())
                    .deletedPaths(changeSet.getDeletedPaths())
                    .fullRebuild(changeSet.isFullRebuild())
                    .build();
            String reason = staleExistingModel ? "embedding-model-mismatch" : null;
            return new PreparedEmbeddingChangeSet(preparedChangeSet, Optional.ofNullable(reason), false);
        }
        EmbeddingBatch embeddingBatch = embedDocuments(changeSet.getSpaceId(), resolvedModel.get(),
                changeSet.getUpserts());
        WikiDocumentChangeSet preparedChangeSet = WikiDocumentChangeSet.builder()
                .spaceId(changeSet.getSpaceId())
                .upserts(changeSet.getUpserts())
                .embeddingUpserts(embeddingBatch.documents())
                .deletedPaths(changeSet.getDeletedPaths())
                .fullRebuild(changeSet.isFullRebuild())
                .build();
        Optional<String> unavailableReason = embeddingBatch.complete()
                ? Optional.empty()
                : Optional.of("embedding-index-incomplete");
        return new PreparedEmbeddingChangeSet(preparedChangeSet, unavailableReason, embeddingBatch.failed());
    }

    private void recordEmbeddingReadiness(String spaceId, PreparedEmbeddingChangeSet preparedEmbeddings,
            boolean authoritativeCoverage) {
        if (preparedEmbeddings.unavailableReason().isPresent()) {
            embeddingUnavailableReasonsBySpaceId.put(spaceId, preparedEmbeddings.unavailableReason().get());
            return;
        }
        if (authoritativeCoverage) {
            clearEmbeddingUnavailableReason(spaceId);
        }
    }

    private void clearEmbeddingUnavailableReason(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            return;
        }
        embeddingUnavailableReasonsBySpaceId.remove(spaceId);
    }

    private EmbeddingBatch embedDocuments(String spaceId, ResolvedEmbeddingModel resolvedModel,
            List<WikiIndexedDocument> documents) {
        if (documents.isEmpty()) {
            return new EmbeddingBatch(List.of(), true, false);
        }
        List<ChunkedInput> chunkedInputs = new ArrayList<>();
        for (WikiIndexedDocument document : documents) {
            List<WikiDocumentChunker.Chunk> chunks = documentChunker.chunk(document.getBody());
            if (chunks.isEmpty() && hasEmbeddableTitle(document)) {
                chunks = List.of(new WikiDocumentChunker.Chunk(0, ""));
            }
            for (WikiDocumentChunker.Chunk chunk : chunks) {
                chunkedInputs.add(new ChunkedInput(document, chunk));
            }
        }
        if (chunkedInputs.isEmpty()) {
            return new EmbeddingBatch(List.of(), true, false);
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
            return new EmbeddingBatch(List.of(), false, true);
        }
        if (response == null || response.getEmbeddings() == null) {
            recordIndexingFailure(spaceId, new IllegalStateException("Embedding provider returned no embeddings"));
            return new EmbeddingBatch(List.of(), false, true);
        }
        Map<String, Integer> expectedChunksByPath = new LinkedHashMap<>();
        for (ChunkedInput entry : chunkedInputs) {
            expectedChunksByPath.merge(entry.document().getPath(), 1, Integer::sum);
        }
        Map<String, List<WikiEmbeddingDocument>> embeddingDocumentsByPath = new LinkedHashMap<>();
        // Pair by index and keep complete documents from short responses. A
        // document with missing chunks is left out so its revision stays stale
        // and the next reconciliation retries it.
        int pairable = Math.min(response.getEmbeddings().size(), chunkedInputs.size());
        String modelId = resolvedModel.model().getId();
        for (int index = 0; index < pairable; index++) {
            List<Double> vector = response.getEmbeddings().get(index);
            if (vector == null || vector.isEmpty()) {
                continue;
            }
            ChunkedInput entry = chunkedInputs.get(index);
            WikiEmbeddingDocument embeddingDocument = WikiEmbeddingDocument.builder()
                    .document(entry.document())
                    .chunkIndex(entry.chunk().index())
                    .chunkText(entry.chunk().text())
                    .embeddingModelId(modelId)
                    .vector(vector)
                    .build();
            embeddingDocumentsByPath.computeIfAbsent(entry.document().getPath(), key -> new ArrayList<>())
                    .add(embeddingDocument);
        }
        List<WikiEmbeddingDocument> embeddingDocuments = new ArrayList<>();
        for (Map.Entry<String, List<WikiEmbeddingDocument>> entry : embeddingDocumentsByPath.entrySet()) {
            if (entry.getValue().size() == expectedChunksByPath.getOrDefault(entry.getKey(), 0)) {
                embeddingDocuments.addAll(entry.getValue());
            }
        }
        boolean complete = embeddingDocumentsByPath.entrySet().stream()
                .filter(entry -> entry.getValue().size() == expectedChunksByPath.getOrDefault(entry.getKey(), 0))
                .count() == expectedChunksByPath.size();
        if (!complete) {
            recordIndexingFailure(spaceId,
                    new IllegalStateException("Embedding provider returned incomplete embeddings"));
        }
        return new EmbeddingBatch(embeddingDocuments, complete, !complete);
    }

    private boolean hasEmbeddableTitle(WikiIndexedDocument document) {
        return document.getTitle() != null && !document.getTitle().isBlank();
    }

    private record PreparedEmbeddingChangeSet(WikiDocumentChangeSet changeSet, Optional<String> unavailableReason,
            boolean indexingFailed) {
    }

    private record EmbeddingBatch(List<WikiEmbeddingDocument> documents, boolean complete, boolean failed) {
    }

    private record ChunkedInput(WikiIndexedDocument document, WikiDocumentChunker.Chunk chunk) {
    }

    private static String buildEmbedInput(ChunkedInput entry) {
        String title = entry.document().getTitle() == null ? "" : entry.document().getTitle().strip();
        String text = entry.chunk().text() == null ? "" : entry.chunk().text().strip();
        if (title.isEmpty()) {
            return text;
        }
        if (text.isEmpty()) {
            return title;
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
        Map<String, String> indexedFullTextRevisions = wikiFullTextIndexPort.listIndexedRevisions(spaceId);
        int staleDocuments = staleFullTextDocumentCount(documents, indexedFullTextRevisions);
        IndexingObservation observation = observationsBySpaceId.getOrDefault(spaceId,
                IndexingObservation.builder().build());
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        String configuredModelId = resolvedModel.map(ResolvedEmbeddingModel::model)
                .map(LlmModelConfig::getId)
                .orElse(null);
        Optional<String> storedModelId = wikiEmbeddingIndexPort.findStoredEmbeddingModelId(spaceId);
        boolean modelMatches = configuredModelId != null && storedModelId.map(configuredModelId::equals).orElse(false);
        Map<String, String> indexedEmbeddingRevisions = modelMatches
                ? wikiEmbeddingIndexPort.listIndexedRevisions(spaceId)
                : Map.of();
        boolean embeddingCoverageComplete = modelMatches
                && embeddingsCoverDocuments(documents, indexedEmbeddingRevisions);
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
                .embeddingsReady(embeddingCoverageComplete)
                .build();
    }

    private int staleFullTextDocumentCount(List<WikiIndexedDocument> documents,
            Map<String, String> indexedFullTextRevisions) {
        int staleDocuments = 0;
        Set<String> actualPaths = new LinkedHashSet<>();
        for (WikiIndexedDocument document : documents) {
            actualPaths.add(document.getPath());
            if (!safeRevision(document).equals(indexedFullTextRevisions.get(document.getPath()))) {
                staleDocuments++;
            }
        }
        for (String indexedPath : indexedFullTextRevisions.keySet()) {
            if (!actualPaths.contains(indexedPath)) {
                staleDocuments++;
            }
        }
        return staleDocuments;
    }

    private boolean embeddingsCoverDocuments(List<WikiIndexedDocument> documents,
            Map<String, String> indexedEmbeddingRevisions) {
        if (documents.isEmpty() || indexedEmbeddingRevisions.isEmpty()) {
            return false;
        }
        Set<String> actualPaths = new LinkedHashSet<>();
        for (WikiIndexedDocument document : documents) {
            actualPaths.add(document.getPath());
            if (!safeRevision(document).equals(indexedEmbeddingRevisions.get(document.getPath()))) {
                return false;
            }
        }
        return indexedEmbeddingRevisions.keySet().stream().allMatch(actualPaths::contains);
    }

    @SuppressWarnings("PMD.NullAssignment")
    private void recordIndexingSuccess(String spaceId) {
        if (spaceId == null || spaceId.isBlank()) {
            return;
        }
        IndexingObservation observation = observationsBySpaceId.get(spaceId);
        if (observation == null) {
            return;
        }
        observation.lastIndexingError = null;
        if (observation.lastFullRebuildAt == null) {
            observationsBySpaceId.remove(spaceId, observation);
        }
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

    private List<String> mergeDistinct(List<String> first, List<String> second) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(first == null ? List.of() : first);
        merged.addAll(second == null ? List.of() : second);
        return merged.stream().toList();
    }

    private String safeRevision(WikiIndexedDocument document) {
        return document.getRevision() == null ? "" : document.getRevision();
    }

    @lombok.Builder
    private static class IndexingObservation {
        private volatile String lastIndexingError;
        private volatile Instant lastFullRebuildAt;
    }

    private static class ScheduledRebuildState {
        /*
         * A rebuild request can arrive while an earlier rebuild is still queued or
         * running. The state intentionally stores one pending rerun bit instead of an
         * unbounded counter: multiple rapid edits only require one follow-up full
         * rebuild after the current catalog snapshot completes.
         */
        private boolean queued;
        private boolean running;
        private boolean rerunRequested;
        private boolean removed;
    }

    private record ResolvedEmbeddingModel(LlmProviderConfig provider, LlmModelConfig model) {
    }
}
