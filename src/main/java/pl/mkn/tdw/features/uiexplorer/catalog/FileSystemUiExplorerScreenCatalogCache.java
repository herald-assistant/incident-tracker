package pl.mkn.tdw.features.uiexplorer.catalog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class FileSystemUiExplorerScreenCatalogCache implements UiExplorerScreenCatalogCache {

    private static final String SCHEMA = "tdw.ui-explorer-screen-catalog-cache";
    private static final int VERSION = 1;

    private final LocalWorkspaceProperties properties;
    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore jsonFileStore;

    @Override
    public Optional<UiExplorerScreenCatalog> find(Key key) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        return jsonFileStore.read(cacheFile(key), UiExplorerScreenCatalogCacheEntry.class)
                .filter(entry -> SCHEMA.equals(entry.schema()))
                .filter(entry -> entry.version() == VERSION)
                .filter(entry -> key.equals(entry.key()))
                .map(UiExplorerScreenCatalogCacheEntry::catalog);
    }

    @Override
    public void save(Key key, UiExplorerScreenCatalog catalog) {
        if (!properties.isEnabled()
                || catalog == null
                || catalog.boundary() == null
                || catalog.boundary().sourceReadCount() <= 0) {
            return;
        }

        try {
            jsonFileStore.writeAtomic(
                    cacheFile(key),
                    new UiExplorerScreenCatalogCacheEntry(SCHEMA, VERSION, Instant.now(), key, catalog)
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to write UI Explorer screen catalog cache key={} error={}", key, exception.getMessage());
        }
    }

    @Override
    public void evict(Key key) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            Files.deleteIfExists(cacheFile(key));
        } catch (IOException exception) {
            log.warn("Failed to evict UI Explorer screen catalog cache key={} error={}", key, exception.getMessage());
        }
    }

    private Path cacheFile(Key key) {
        return paths.root()
                .resolve("ui-explorer")
                .resolve("screen-catalog-cache")
                .resolve(cacheKeyHash(key) + ".json");
    }

    private String cacheKeyHash(Key key) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = String.join("\n",
                    safe(key.systemId()),
                    safe(key.systemLabel()),
                    safe(key.requestedRef()),
                    safe(key.gitLabGroup()),
                    safe(key.gitLabProjectName()),
                    safe(key.repositoryId()),
                    safe(key.projectPath()),
                    safe(key.searchMode()),
                    String.join("|", key.pathPrefixes()),
                    Integer.toString(key.maxRouteNodes()),
                    Integer.toString(key.maxRouteFiles()),
                    Integer.toString(key.maxSourceReads()),
                    Integer.toString(key.maxAliasResolutions()),
                    Integer.toString(key.maxImportDepth())
            ).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}

record UiExplorerScreenCatalogCacheEntry(
        String schema,
        int version,
        Instant cachedAt,
        UiExplorerScreenCatalogCache.Key key,
        UiExplorerScreenCatalog catalog
) {
}
