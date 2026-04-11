package dev.golemcore.brain.adapter.out.index.lucene;

import dev.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiSearchDocument;
import dev.golemcore.brain.domain.WikiSearchHit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LuceneWikiFullTextIndexAdapter implements WikiFullTextIndexPort {

    private static final String INDEX_ROOT = ".indexes/lucene";
    private static final String FIELD_SPACE_ID = "spaceId";
    private static final String FIELD_ID = "id";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_PARENT_PATH = "parentPath";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_BODY = "body";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_UPDATED_AT = "updatedAt";
    private static final String FIELD_REVISION = "revision";

    private final WikiProperties wikiProperties;

    @Override
    public synchronized void applyChanges(WikiDocumentChangeSet changeSet) {
        if (changeSet == null || changeSet.getSpaceId() == null || changeSet.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(indexPath(changeSet.getSpaceId()));
            try (StandardAnalyzer analyzer = new StandardAnalyzer();
                    Directory directory = openDirectory(changeSet.getSpaceId());
                    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                if (changeSet.isFullRebuild()) {
                    writer.deleteAll();
                }
                for (String deletedPath : safeList(changeSet.getDeletedPaths())) {
                    writer.deleteDocuments(new Term(FIELD_PATH, normalizePath(deletedPath)));
                }
                for (WikiIndexedDocument document : safeList(changeSet.getUpserts())) {
                    writer.updateDocument(new Term(FIELD_PATH, document.getPath()),
                            toLuceneDocument(changeSet.getSpaceId(), document));
                }
                writer.commit();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update Lucene wiki index", exception);
        }
    }

    @Override
    public synchronized List<WikiSearchHit> search(String spaceId, String query, int limit) {
        if (!hasIndex(spaceId) || query == null || query.isBlank()) {
            return List.of();
        }
        try (StandardAnalyzer analyzer = new StandardAnalyzer(); Directory directory = openDirectory(spaceId)) {
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[] { FIELD_TITLE, FIELD_BODY, FIELD_PATH },
                        analyzer);
                Query parsedQuery = parser.parse(MultiFieldQueryParser.escape(query.trim()));
                TopDocs topDocs = searcher.search(parsedQuery, Math.max(1, limit));
                List<WikiSearchHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    hits.add(toSearchHit(searcher.doc(scoreDoc.doc), query));
                }
                return hits;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to search Lucene wiki index", exception);
        } catch (org.apache.lucene.queryparser.classic.ParseException exception) {
            return List.of();
        }
    }

    @Override
    public synchronized List<String> listIndexedPaths(String spaceId) {
        if (!hasIndex(spaceId)) {
            return List.of();
        }
        try (Directory directory = openDirectory(spaceId); DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), Math.max(1, reader.numDocs()));
            List<String> paths = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                paths.add(searcher.doc(scoreDoc.doc).get(FIELD_PATH));
            }
            paths.sort(String.CASE_INSENSITIVE_ORDER);
            return paths;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list Lucene wiki index paths", exception);
        }
    }

    @Override
    public synchronized Map<String, String> listIndexedRevisions(String spaceId) {
        if (!hasIndex(spaceId)) {
            return Map.of();
        }
        try (Directory directory = openDirectory(spaceId); DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), Math.max(1, reader.numDocs()));
            Map<String, String> revisions = new LinkedHashMap<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document document = searcher.doc(scoreDoc.doc);
                revisions.put(document.get(FIELD_PATH), document.get(FIELD_REVISION));
            }
            return revisions;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list Lucene wiki index revisions", exception);
        }
    }

    @Override
    public synchronized int count(String spaceId) {
        if (!hasIndex(spaceId)) {
            return 0;
        }
        try (Directory directory = openDirectory(spaceId); DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to count Lucene wiki index documents", exception);
        }
    }

    private Document toLuceneDocument(String spaceId, WikiIndexedDocument document) {
        Document luceneDocument = new Document();
        luceneDocument.add(new StringField(FIELD_SPACE_ID, spaceId, Field.Store.YES));
        luceneDocument.add(new StringField(FIELD_ID, document.getId(), Field.Store.YES));
        luceneDocument.add(new StringField(FIELD_PATH, document.getPath(), Field.Store.YES));
        luceneDocument.add(new StringField(FIELD_PARENT_PATH, nullToEmpty(document.getParentPath()), Field.Store.YES));
        luceneDocument.add(new TextField(FIELD_TITLE, nullToEmpty(document.getTitle()), Field.Store.YES));
        luceneDocument.add(new TextField(FIELD_BODY, nullToEmpty(document.getBody()), Field.Store.YES));
        luceneDocument.add(new StringField(FIELD_KIND, document.getKind().name(), Field.Store.YES));
        luceneDocument.add(new StringField(FIELD_UPDATED_AT, toEpochMillis(document.getUpdatedAt()), Field.Store.YES));
        luceneDocument.add(new StringField(FIELD_REVISION, nullToEmpty(document.getRevision()), Field.Store.YES));
        return luceneDocument;
    }

    private WikiSearchHit toSearchHit(Document document, String query) {
        WikiSearchDocument searchDocument = WikiSearchDocument.builder()
                .id(document.get(FIELD_ID))
                .path(document.get(FIELD_PATH))
                .parentPath(emptyToNull(document.get(FIELD_PARENT_PATH)))
                .title(document.get(FIELD_TITLE))
                .body(document.get(FIELD_BODY))
                .kind(WikiNodeKind.valueOf(document.get(FIELD_KIND)))
                .build();
        return WikiSearchHit.builder()
                .id(searchDocument.getId())
                .path(searchDocument.getPath())
                .title(searchDocument.getTitle())
                .excerpt(searchDocument.buildExcerpt(query.toLowerCase(Locale.ROOT)))
                .parentPath(searchDocument.getParentPath())
                .kind(searchDocument.getKind())
                .build();
    }

    private Directory openDirectory(String spaceId) throws IOException {
        return FSDirectory.open(indexPath(spaceId));
    }

    private boolean hasIndex(String spaceId) {
        try {
            Path path = indexPath(spaceId);
            if (!Files.isDirectory(path)) {
                return false;
            }
            try (Directory directory = FSDirectory.open(path)) {
                return DirectoryReader.indexExists(directory);
            }
        } catch (IOException exception) {
            return false;
        }
    }

    private Path indexPath(String spaceId) {
        return wikiProperties.getStorageRoot().resolve(INDEX_ROOT).resolve(spaceId);
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String toEpochMillis(Instant instant) {
        return Long.toString(instant == null ? 0L : instant.toEpochMilli());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
