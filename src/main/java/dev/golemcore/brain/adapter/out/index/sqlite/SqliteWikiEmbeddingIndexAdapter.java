package dev.golemcore.brain.adapter.out.index.sqlite;

import dev.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiEmbeddingDocument;
import dev.golemcore.brain.domain.WikiEmbeddingSearchHit;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiSearchDocument;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
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
                for (WikiEmbeddingDocument embeddingDocument : safeList(changeSet.getEmbeddingUpserts())) {
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
            return documents.stream()
                    .map(document -> toHit(document, embedding))
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
                    "select path from wiki_embeddings where space_id = ? order by lower(path)")) {
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
                    "select path, revision from wiki_embeddings where space_id = ? order by lower(path)")) {
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
                    "select count(*) from wiki_embeddings where space_id = ?")) {
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
        try (Statement statement = connection.createStatement()) {
            statement.execute("create table if not exists wiki_embeddings ("
                    + "space_id text not null, "
                    + "path text not null, "
                    + "id text not null, "
                    + "parent_path text, "
                    + "title text not null, "
                    + "body text not null, "
                    + "kind text not null, "
                    + "revision text, "
                    + "updated_at integer not null, "
                    + "vector blob not null, "
                    + "primary key (space_id, path))");
            statement.execute("create index if not exists idx_wiki_embeddings_space "
                    + "on wiki_embeddings(space_id)");
        }
    }

    private void upsertDocument(Connection connection, String spaceId, WikiEmbeddingDocument embeddingDocument)
            throws SQLException {
        WikiIndexedDocument document = embeddingDocument.getDocument();
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wiki_embeddings "
                        + "(space_id, path, id, parent_path, title, body, kind, revision, updated_at, vector) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on conflict(space_id, path) do update set "
                        + "id = excluded.id, parent_path = excluded.parent_path, title = excluded.title, "
                        + "body = excluded.body, kind = excluded.kind, revision = excluded.revision, "
                        + "updated_at = excluded.updated_at, vector = excluded.vector")) {
            statement.setString(1, spaceId);
            statement.setString(2, document.getPath());
            statement.setString(3, document.getId());
            statement.setString(4, document.getParentPath());
            statement.setString(5, document.getTitle());
            statement.setString(6, document.getBody());
            statement.setString(7, document.getKind().name());
            statement.setString(8, document.getRevision());
            statement.setLong(9, toEpochMillis(document.getUpdatedAt()));
            statement.setBytes(10, toBytes(embeddingDocument.getVector()));
            statement.executeUpdate();
        }
    }

    private List<StoredEmbeddingDocument> readSpaceDocuments(Connection connection, String spaceId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id, path, parent_path, title, body, kind, vector from wiki_embeddings where space_id = ?")) {
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
                            WikiNodeKind.valueOf(resultSet.getString("kind")),
                            fromBytes(resultSet.getBytes("vector"))));
                }
                return documents;
            }
        }
    }

    private WikiEmbeddingSearchHit toHit(StoredEmbeddingDocument document, List<Double> queryEmbedding) {
        WikiSearchDocument searchDocument = WikiSearchDocument.builder()
                .id(document.id())
                .path(document.path())
                .parentPath(document.parentPath())
                .title(document.title())
                .body(document.body())
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
        int size = Math.min(left.size(), right.size());
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
            WikiNodeKind kind,
            List<Double> vector) {
    }
}
