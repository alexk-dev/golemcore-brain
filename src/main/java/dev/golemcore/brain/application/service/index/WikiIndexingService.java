package dev.golemcore.brain.application.service.index;

import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import dev.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import dev.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiEmbeddingDocument;
import dev.golemcore.brain.domain.WikiEmbeddingSearchHit;
import dev.golemcore.brain.domain.WikiIndexMetadata;
import dev.golemcore.brain.domain.WikiIndexStatus;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WikiIndexingService {

    private static final int SEARCH_LIMIT = 50;
    private static final int EMBEDDING_SEARCH_LIMIT = 10;
    private static final String INDEX_MODE = "lucene+sqlite-embeddings";

    private final WikiDocumentCatalogPort wikiDocumentCatalogPort;
    private final WikiFullTextIndexPort wikiFullTextIndexPort;
    private final WikiEmbeddingIndexPort wikiEmbeddingIndexPort;
    private final LlmSettingsRepository llmSettingsRepository;
    private final LlmEmbeddingPort llmEmbeddingPort;

    public void synchronizeSpace(String spaceId) {
        List<WikiIndexedDocument> documents = wikiDocumentCatalogPort.listDocuments(spaceId);
        List<String> actualPaths = documents.stream().map(WikiIndexedDocument::getPath).toList();
        Map<String, String> indexedRevisions = wikiFullTextIndexPort.listIndexedRevisions(spaceId);
        List<String> deletedPaths = pathsMissingFrom(new ArrayList<>(indexedRevisions.keySet()), actualPaths);
        boolean embeddingsEnabled = resolveEmbeddingModel().isPresent();
        Map<String, String> indexedEmbeddingRevisions = embeddingsEnabled
                ? wikiEmbeddingIndexPort.listIndexedRevisions(spaceId)
                : Map.of();
        List<WikiIndexedDocument> changedDocuments = documents.stream()
                .filter(document -> !safeRevision(document).equals(indexedRevisions.get(document.getPath()))
                        || (embeddingsEnabled
                                && !safeRevision(document).equals(indexedEmbeddingRevisions.get(document.getPath()))))
                .toList();
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(changedDocuments)
                .embeddingUpserts(List.of())
                .deletedPaths(deletedPaths)
                .fullRebuild(false)
                .build());
    }

    public void recordUpsert(String spaceId, WikiIndexedDocument document) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(List.of(document))
                .embeddingUpserts(List.of())
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());
    }

    public void recordUpserts(String spaceId, List<WikiIndexedDocument> documents) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(documents == null ? List.of() : documents)
                .embeddingUpserts(List.of())
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());
    }

    public void recordDeletes(String spaceId, List<String> deletedPaths) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(List.of())
                .embeddingUpserts(List.of())
                .deletedPaths(deletedPaths == null ? List.of() : deletedPaths)
                .fullRebuild(false)
                .build());
    }

    public void rebuildSpace(String spaceId) {
        applyChanges(WikiDocumentChangeSet.builder()
                .spaceId(spaceId)
                .upserts(wikiDocumentCatalogPort.listDocuments(spaceId))
                .embeddingUpserts(List.of())
                .deletedPaths(List.of())
                .fullRebuild(true)
                .build());
    }

    public List<WikiSearchHit> search(String spaceId, String query) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        synchronizeSpace(spaceId);
        return wikiFullTextIndexPort.search(spaceId, normalizedQuery, SEARCH_LIMIT);
    }

    public List<WikiEmbeddingSearchHit> embeddingSearch(String spaceId, String query) {
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
        synchronizeSpace(spaceId);
        return wikiEmbeddingIndexPort.search(spaceId, queryEmbedding.get(), EMBEDDING_SEARCH_LIMIT);
    }

    public WikiIndexStatus getStatus(String spaceId) {
        synchronizeSpace(spaceId);
        WikiIndexMetadata metadata = metadata(spaceId);
        return WikiIndexStatus.builder()
                .mode(INDEX_MODE)
                .ready(metadata.isReady())
                .indexedDocuments(metadata.getDocumentCount())
                .embeddingDocuments(metadata.getEmbeddingDocumentCount())
                .lastUpdatedAt(metadata.getLastUpdatedAt())
                .embeddingsReady(metadata.isEmbeddingsReady())
                .build();
    }

    private void applyChanges(WikiDocumentChangeSet changeSet) {
        if (changeSet == null || changeSet.isEmpty()) {
            return;
        }
        WikiDocumentChangeSet normalizedChangeSet = normalizeChangeSet(changeSet);
        wikiFullTextIndexPort.applyChanges(normalizedChangeSet);
        wikiEmbeddingIndexPort.applyChanges(withEmbeddings(normalizedChangeSet));
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

    private WikiDocumentChangeSet withEmbeddings(WikiDocumentChangeSet changeSet) {
        if (changeSet.getUpserts().isEmpty()) {
            return changeSet;
        }
        Optional<ResolvedEmbeddingModel> resolvedModel = resolveEmbeddingModel();
        if (resolvedModel.isEmpty()) {
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
                .embeddingUpserts(embedDocuments(resolvedModel.get(), changeSet.getUpserts()))
                .deletedPaths(changeSet.getDeletedPaths())
                .fullRebuild(changeSet.isFullRebuild())
                .build();
    }

    private List<WikiEmbeddingDocument> embedDocuments(ResolvedEmbeddingModel resolvedModel,
            List<WikiIndexedDocument> documents) {
        List<String> inputs = documents.stream().map(this::embeddingText).toList();
        LlmEmbeddingResponse response;
        try {
            response = llmEmbeddingPort.embed(LlmEmbeddingRequest.builder()
                    .provider(resolvedModel.provider())
                    .model(resolvedModel.model())
                    .inputs(inputs)
                    .build());
        } catch (RuntimeException exception) {
            return List.of();
        }
        if (response == null || response.getEmbeddings() == null
                || response.getEmbeddings().size() != documents.size()) {
            return List.of();
        }
        List<WikiEmbeddingDocument> embeddingDocuments = new ArrayList<>();
        for (int index = 0; index < documents.size(); index++) {
            List<Double> vector = response.getEmbeddings().get(index);
            if (vector != null && !vector.isEmpty()) {
                embeddingDocuments.add(WikiEmbeddingDocument.builder()
                        .document(documents.get(index))
                        .vector(vector)
                        .build());
            }
        }
        return embeddingDocuments;
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
        return WikiIndexMetadata.builder()
                .documentCount(fullTextCount)
                .embeddingDocumentCount(embeddingCount)
                .lastUpdatedAt(lastUpdatedAt)
                .ready(fullTextCount >= documents.size())
                .embeddingsReady(embeddingCount > 0)
                .build();
    }

    private List<String> pathsMissingFrom(List<String> existingPaths, List<String> actualPaths) {
        Set<String> actual = new LinkedHashSet<>(actualPaths == null ? List.of() : actualPaths);
        return (existingPaths == null ? List.<String>of() : existingPaths).stream()
                .filter(path -> !actual.contains(path))
                .toList();
    }

    private String embeddingText(WikiIndexedDocument document) {
        return document.getTitle() + "\n\n" + document.getBody();
    }

    private String safeRevision(WikiIndexedDocument document) {
        return document.getRevision() == null ? "" : document.getRevision();
    }

    private record ResolvedEmbeddingModel(LlmProviderConfig provider, LlmModelConfig model) {
    }
}
