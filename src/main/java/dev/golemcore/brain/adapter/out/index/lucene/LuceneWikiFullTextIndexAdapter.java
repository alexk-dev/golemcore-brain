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
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
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
    public synchronized void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
        if (changeSet == null || spaceId == null || spaceId.isBlank() || changeSet.isEmpty()) {
            return;
        }
        try {
            Files.createDirectories(indexPath(spaceId));
            try (StandardAnalyzer analyzer = new StandardAnalyzer();
                    Directory directory = openDirectory(spaceId);
                    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                if (changeSet.isFullRebuild()) {
                    writer.deleteAll();
                }
                for (String deletedPath : safeList(changeSet.getDeletedPaths())) {
                    writer.deleteDocuments(new Term(FIELD_PATH, normalizePath(deletedPath)));
                }
                for (WikiIndexedDocument document : safeList(changeSet.getUpserts())) {
                    writer.updateDocument(new Term(FIELD_PATH, document.getPath()),
                            toLuceneDocument(spaceId, document));
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
        String normalizedQuery = query.trim();
        try (StandardAnalyzer analyzer = new StandardAnalyzer(); Directory directory = openDirectory(spaceId)) {
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                if (hasWildcard(normalizedQuery)) {
                    return wildcardSearch(searcher, reader, normalizedQuery, limit);
                }
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[] { FIELD_TITLE, FIELD_BODY, FIELD_PATH },
                        analyzer);
                Query parsedQuery = parser.parse(MultiFieldQueryParser.escape(normalizedQuery));
                TopDocs topDocs = searcher.search(parsedQuery, Math.max(1, limit));
                List<WikiSearchHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    hits.add(toSearchHit(searcher.doc(scoreDoc.doc), normalizedQuery));
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

    private List<WikiSearchHit> wildcardSearch(IndexSearcher searcher, DirectoryReader reader, String query, int limit)
            throws IOException {
        TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), Math.max(1, reader.numDocs()));
        List<ScoredSearchHit> scoredHits = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            WikiSearchDocument searchDocument = toSearchDocument(searcher.doc(scoreDoc.doc));
            int score = wildcardScore(searchDocument, query);
            if (score > 0) {
                scoredHits.add(new ScoredSearchHit(toSearchHit(searchDocument, query), score));
            }
        }
        scoredHits.sort(Comparator.comparingInt(ScoredSearchHit::score)
                .reversed()
                .thenComparing(scoredHit -> scoredHit.hit().getPath(), String.CASE_INSENSITIVE_ORDER));
        return scoredHits.stream()
                .limit(Math.max(1, limit))
                .map(ScoredSearchHit::hit)
                .toList();
    }

    private int wildcardScore(WikiSearchDocument document, String query) {
        List<String> tokens = tokenizeQuery(query);
        if (tokens.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (String token : tokens) {
            int tokenScore = tokenScore(document, token);
            if (tokenScore == 0) {
                return 0;
            }
            score += tokenScore;
        }
        return score;
    }

    private int tokenScore(WikiSearchDocument document, String token) {
        if ("*".equals(token)) {
            return 1;
        }
        if (isWildcardToken(token)) {
            Pattern pattern = Pattern.compile(globToRegex(token.toLowerCase(Locale.ROOT)));
            return wildcardTokenScore(document, pattern);
        }
        return plainTokenScore(document, token.toLowerCase(Locale.ROOT));
    }

    private int wildcardTokenScore(WikiSearchDocument document, Pattern pattern) {
        int score = 0;
        if (pattern.matcher(normalize(document.getPath())).find()) {
            score += 80;
        }
        if (pattern.matcher(normalizePathForMask(document.getPath())).find()) {
            score += 60;
        }
        if (pattern.matcher(normalize(document.getTitle())).find()) {
            score += 40;
        }
        if (pattern.matcher(normalize(document.getBody())).find()) {
            score += 10;
        }
        return score;
    }

    private int plainTokenScore(WikiSearchDocument document, String token) {
        int score = 0;
        if (normalize(document.getPath()).contains(token)) {
            score += 80;
        }
        if (normalizePathForMask(document.getPath()).contains(token)) {
            score += 60;
        }
        if (normalize(document.getTitle()).contains(token)) {
            score += 40;
        }
        if (normalize(document.getBody()).contains(token)) {
            score += 10;
        }
        return score;
    }

    private boolean hasWildcard(String query) {
        return query.indexOf('*') >= 0 || query.indexOf('?') >= 0;
    }

    private boolean isWildcardToken(String token) {
        return token.indexOf('*') >= 0 || token.indexOf('?') >= 0;
    }

    private String globToRegex(String token) {
        StringBuilder regex = new StringBuilder();
        for (int index = 0; index < token.length(); index++) {
            char character = token.charAt(index);
            if (character == '*') {
                regex.append(".*");
            } else if (character == '?') {
                regex.append('.');
            } else {
                appendEscapedRegexCharacter(regex, character);
            }
        }
        return regex.toString();
    }

    private void appendEscapedRegexCharacter(StringBuilder regex, char character) {
        if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
            regex.append('\\');
        }
        regex.append(character);
    }

    private String normalizePathForMask(String path) {
        return normalize(path).replace('/', ' ').replace('-', ' ').replace('_', ' ');
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private List<String> tokenizeQuery(String query) {
        return List.of(query.trim().split("\\s+"))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
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
        return toSearchHit(toSearchDocument(document), query);
    }

    private WikiSearchDocument toSearchDocument(Document document) {
        return WikiSearchDocument.builder()
                .id(document.get(FIELD_ID))
                .path(document.get(FIELD_PATH))
                .parentPath(emptyToNull(document.get(FIELD_PARENT_PATH)))
                .title(document.get(FIELD_TITLE))
                .body(document.get(FIELD_BODY))
                .kind(WikiNodeKind.valueOf(document.get(FIELD_KIND)))
                .build();
    }

    private WikiSearchHit toSearchHit(WikiSearchDocument searchDocument, String query) {
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

    private record ScoredSearchHit(WikiSearchHit hit, int score) {
    }
}
