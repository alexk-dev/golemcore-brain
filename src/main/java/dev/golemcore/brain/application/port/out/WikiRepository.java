package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.WikiAsset;
import dev.golemcore.brain.domain.WikiAssetContent;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeReference;
import dev.golemcore.brain.domain.WikiPageDocument;
import dev.golemcore.brain.domain.WikiPageHistoryEntry;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface WikiRepository extends WikiDocumentCatalogPort {

    void initialize();

    /**
     * Ensure filesystem storage for a space exists (create the root dir and a
     * welcome page, optionally seed demo content). Idempotent.
     */
    void initializeSpace(String spaceId);

    Optional<WikiNodeReference> findReference(String path);

    WikiNodeReference getRootReference();

    List<WikiNodeReference> listChildren(WikiNodeReference parentReference);

    WikiPageDocument readDocument(WikiNodeReference nodeReference);

    WikiPageDocument createPage(String parentPath, String title, String slug, String content,
            dev.golemcore.brain.domain.WikiNodeKind kind);

    WikiPageDocument updatePage(String path, String title, String slug, String content, String expectedRevision,
            String actor, String reason, String summary);

    void deletePage(String path);

    WikiPageDocument movePage(String path, String targetParentPath, String targetSlug, String beforeSlug);

    WikiPageDocument copyPage(String path, String targetParentPath, String targetSlug, String beforeSlug);

    WikiPageDocument convertPage(String path, dev.golemcore.brain.domain.WikiNodeKind targetKind);

    void sortChildren(String path, List<String> orderedSlugs);

    List<WikiNodeReference> flatten();

    @Override
    List<WikiIndexedDocument> listDocuments(String spaceId);

    List<WikiPageHistoryEntry> listPageHistory(String path);

    WikiPageDocument restorePageVersion(String path, String versionId, String actor, String reason, String summary);

    dev.golemcore.brain.domain.WikiPageHistoryVersion readPageHistoryVersion(String path, String versionId);

    List<WikiAsset> listAssets(String path);

    WikiAsset saveAsset(String path, String fileName, String contentType, InputStream inputStream);

    WikiAsset renameAsset(String path, String oldName, String newName);

    void deleteAsset(String path, String assetName);

    WikiAssetContent openAsset(String path, String assetName);
}
