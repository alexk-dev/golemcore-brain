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

package me.golemcore.brain.adapter.out.index.sqlite;

import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingDocument;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.WikiSearchDocument;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "brain.indexing.embedding-adapter", havingValue = "sqlite", matchIfMissing = true)
@RequiredArgsConstructor
public class SqliteWikiEmbeddingIndexAdapter implements WikiEmbeddingIndexPort {

    private static final String INDEX_ROOT = ".indexes/embeddings";
    private static final String DATABASE_FILE = "embeddings.sqlite";

    private final WikiProperties wikiProperties;

    @Override
    public synchronized void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
        if (changeSet == null || spaceId == null || spaceId.isBlank() || hasNoEmbeddingIndexChanges(changeSet)) {
            return;
        }
        try {
            Files.createDirectories(databaseDirectory());
            try (Connection connection = openConnection()) {
                initializeSchema(connection);
                if (changeSet.isFullRebuild()) {
                    deleteSpace(connection, spaceId);
                }
                for (String deletedPath : safeList(changeSet.getDeletedPaths())) {
                    deletePath(connection, spaceId, deletedPath);
                }
                java.util.Set<String> wipedPaths = new java.util.HashSet<>();
                for (WikiEmbeddingDocument embeddingDocument : safeList(changeSet.getEmbeddingUpserts())) {
                    String path = embeddingDocument.getDocument().getPath();
                    if (wipedPaths.add(path)) {
                        deletePath(connection, spaceId, path);
                    }
                    upsertDocument(connection, spaceId, embeddingDocument);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update SQLite embedding index", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Failed to prepare SQLite embedding index", exception);
        }
    }

    @Override
    public synchronized List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit) {
        if (embedding == null || embedding.isEmpty() || !Files.exists(databasePath())) {
            return List.of();
        }
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            List<StoredEmbeddingDocument> documents = readSpaceDocuments(connection, spaceId);
            LinkedHashMap<String, WikiEmbeddingSearchHit> bestByPath = new LinkedHashMap<>();
            for (StoredEmbeddingDocument document : documents) {
                WikiEmbeddingSearchHit hit = toHit(document, embedding);
                WikiEmbeddingSearchHit current = bestByPath.get(hit.getPath());
                if (current == null || hit.getScore() > current.getScore()) {
                    bestByPath.put(hit.getPath(), hit);
                }
            }
            return bestByPath.values().stream()
                    .sorted(Comparator.comparingDouble(WikiEmbeddingSearchHit::getScore).reversed())
                    .limit(Math.max(1, limit))
                    .toList();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to search SQLite embedding index", exception);
        }
    }

    @Override
    public synchronized List<String> listIndexedPaths(String spaceId) {
        if (!Files.exists(databasePath())) {
            return List.of();
        }
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "select distinct path from wiki_embeddings where space_id = ? order by lower(path)")) {
                statement.setString(1, spaceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<String> paths = new ArrayList<>();
                    while (resultSet.next()) {
                        paths.add(resultSet.getString("path"));
                    }
                    return paths;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list SQLite embedding index paths", exception);
        }
    }

    @Override
    public synchronized Map<String, String> listIndexedRevisions(String spaceId) {
        if (!Files.exists(databasePath())) {
            return Map.of();
        }
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "select path, revision from wiki_embeddings where space_id = ? and chunk_index = 0 "
                            + "order by lower(path)")) {
                statement.setString(1, spaceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    Map<String, String> revisions = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        revisions.put(resultSet.getString("path"), resultSet.getString("revision"));
                    }
                    return revisions;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to list SQLite embedding index revisions", exception);
        }
    }

    @Override
    public synchronized int count(String spaceId) {
        if (!Files.exists(databasePath())) {
            return 0;
        }
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "select count(distinct path) from wiki_embeddings where space_id = ?")) {
                statement.setString(1, spaceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() ? resultSet.getInt(1) : 0;
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count SQLite embedding index documents", exception);
        }
    }

    private boolean hasNoEmbeddingIndexChanges(WikiDocumentChangeSet changeSet) {
        boolean hasEmbeddings = changeSet.getEmbeddingUpserts() != null && !changeSet.getEmbeddingUpserts().isEmpty();
        boolean hasDeletes = changeSet.getDeletedPaths() != null && !changeSet.getDeletedPaths().isEmpty();
        return !changeSet.isFullRebuild() && !hasEmbeddings && !hasDeletes;
    }

    private void initializeSchema(Connection connection) throws SQLException {
        if (tableExists(connection) && !hasChunkIndexInPrimaryKey(connection)) {
            // Legacy PK was (space_id, path) — cannot be altered to include
            // chunk_index in SQLite. The table is a derived index, so drop it
            // and let reconciliation rebuild it from the catalog.
            try (Statement statement = connection.createStatement()) {
                statement.execute("drop table wiki_embeddings");
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists wiki_embeddings ("
                    + "space_id text not null, "
                    + "path text not null, "
                    + "chunk_index integer not null default 0, "
                    + "id text not null, "
                    + "parent_path text, "
                    + "title text not null, "
                    + "body text not null, "
                    + "chunk_text text, "
                    + "kind text not null, "
                    + "revision text, "
                    + "updated_at integer not null, "
                    + "embedding_model_id text, "
                    + "vector blob not null, "
                    + "primary key (space_id, path, chunk_index))");
            statement.execute("create index if not exists idx_wiki_embeddings_space "
                    + "on wiki_embeddings(space_id)");
        }
        addColumnIfMissing(connection, "embedding_model_id", "text");
    }

    private void addColumnIfMissing(Connection connection, String columnName, String columnType) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet info = statement.executeQuery("pragma table_info(wiki_embeddings)")) {
            while (info.next()) {
                if (columnName.equalsIgnoreCase(info.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("alter table wiki_embeddings add column " + columnName + " " + columnType);
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from sqlite_master where type = 'table' and name = 'wiki_embeddings'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean hasChunkIndexInPrimaryKey(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet info = statement.executeQuery("pragma table_info(wiki_embeddings)")) {
            while (info.next()) {
                if ("chunk_index".equalsIgnoreCase(info.getString("name")) && info.getInt("pk") > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void upsertDocument(Connection connection, String spaceId, WikiEmbeddingDocument embeddingDocument)
            throws SQLException {
        WikiIndexedDocument document = embeddingDocument.getDocument();
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wiki_embeddings "
                        + "(space_id, path, chunk_index, id, parent_path, title, body, chunk_text, "
                        + "kind, revision, updated_at, embedding_model_id, vector) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(space_id, path, chunk_index) do update set "
                        + "id = excluded.id, parent_path = excluded.parent_path, title = excluded.title, "
                        + "body = excluded.body, chunk_text = excluded.chunk_text, kind = excluded.kind, "
                        + "revision = excluded.revision, updated_at = excluded.updated_at, "
                        + "embedding_model_id = excluded.embedding_model_id, "
                        + "vector = excluded.vector")) {
            statement.setString(1, spaceId);
            statement.setString(2, document.getPath());
            statement.setInt(3, embeddingDocument.getChunkIndex());
            statement.setString(4, document.getId());
            statement.setString(5, document.getParentPath());
            statement.setString(6, document.getTitle());
            statement.setString(7, document.getBody());
            statement.setString(8, embeddingDocument.getChunkText());
            statement.setString(9, document.getKind().name());
            statement.setString(10, document.getRevision());
            statement.setLong(11, toEpochMillis(document.getUpdatedAt()));
            statement.setString(12, embeddingDocument.getEmbeddingModelId());
            statement.setBytes(13, toBytes(embeddingDocument.getVector()));
            statement.executeUpdate();
        }
    }

    @Override
    public synchronized Optional<String> findStoredEmbeddingModelId(String spaceId) {
        if (!Files.exists(databasePath())) {
            return Optional.empty();
        }
        try (Connection connection = openConnection()) {
            initializeSchema(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "select embedding_model_id from wiki_embeddings "
                            + "where space_id = ? and embedding_model_id is not null limit 1")) {
                statement.setString(1, spaceId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String value = resultSet.getString(1);
                        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
                    }
                    return Optional.empty();
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read stored embedding model id", exception);
        }
    }

    private List<StoredEmbeddingDocument> readSpaceDocuments(Connection connection, String spaceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, path, parent_path, title, body, chunk_text, kind, vector "
                        + "from wiki_embeddings where space_id = ?")) {
            statement.setString(1, spaceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<StoredEmbeddingDocument> documents = new ArrayList<>();
                while (resultSet.next()) {
                    documents.add(new StoredEmbeddingDocument(
                            resultSet.getString("id"),
                            resultSet.getString("path"),
                            resultSet.getString("parent_path"),
                            resultSet.getString("title"),
                            resultSet.getString("body"),
                            resultSet.getString("chunk_text"),
                            WikiNodeKind.valueOf(resultSet.getString("kind")),
                            fromBytes(resultSet.getBytes("vector"))));
                }
                return documents;
            }
        }
    }

    private WikiEmbeddingSearchHit toHit(StoredEmbeddingDocument document, List<Double> queryEmbedding) {
        // The chunk_text — when present — is the actual slice of content that
        // produced the stored vector, so excerpts should describe it rather
        // than the raw document body the chunk was cut from.
        String excerptSource = document.chunkText() != null && !document.chunkText().isBlank()
                ? document.chunkText()
                : document.body();
        WikiSearchDocument searchDocument = WikiSearchDocument.builder()
                .id(document.id())
                .path(document.path())
                .parentPath(document.parentPath())
                .title(document.title())
                .body(excerptSource)
                .kind(document.kind())
                .build();
        return WikiEmbeddingSearchHit.builder()
                .id(document.id())
                .path(document.path())
                .title(document.title())
                .excerpt(searchDocument.buildExcerpt(document.title().toLowerCase(Locale.ROOT)))
                .parentPath(document.parentPath())
                .kind(document.kind())
                .score(cosineSimilarity(document.vector(), queryEmbedding))
                .build();
    }

    private void deleteSpace(Connection connection, String spaceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from wiki_embeddings where space_id = ?")) {
            statement.setString(1, spaceId);
            statement.executeUpdate();
        }
    }

    private void deletePath(Connection connection, String spaceId, String path) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from wiki_embeddings where space_id = ? and path = ?")) {
            statement.setString(1, spaceId);
            statement.setString(2, path);
            statement.executeUpdate();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databasePath().toAbsolutePath());
    }

    private Path databasePath() {
        return databaseDirectory().resolve(DATABASE_FILE);
    }

    private Path databaseDirectory() {
        return wikiProperties.getStorageRoot().resolve(INDEX_ROOT);
    }

    private byte[] toBytes(List<Double> vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.size() * Double.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (Double value : vector) {
            buffer.putDouble(value == null ? 0.0d : value);
        }
        return buffer.array();
    }

    private List<Double> fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Double> vector = new ArrayList<>();
        while (buffer.remaining() >= Double.BYTES) {
            vector.add(buffer.getDouble());
        }
        return vector;
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new IllegalStateException("Embedding dimension mismatch: stored=" + left.size()
                    + " query=" + right.size()
                    + " — the embedding model likely changed; rebuild the embedding index.");
        }
        int size = left.size();
        if (size == 0) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int index = 0; index < size; index++) {
            double leftValue = left.get(index) == null ? 0.0d : left.get(index);
            double rightValue = right.get(index) == null ? 0.0d : right.get(index);
            dot += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private long toEpochMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record StoredEmbeddingDocument(
            String id,
            String path,
            String parentPath,
            String title,
            String body,
            String chunkText,
            WikiNodeKind kind,
            List<Double> vector) {
    }
}
