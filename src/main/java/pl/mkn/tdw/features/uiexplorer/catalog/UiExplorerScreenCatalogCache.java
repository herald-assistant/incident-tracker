package pl.mkn.tdw.features.uiexplorer.catalog;

import java.util.List;
import java.util.Optional;

public interface UiExplorerScreenCatalogCache {

    Optional<UiExplorerScreenCatalog> find(Key key);

    void save(Key key, UiExplorerScreenCatalog catalog);

    void evict(Key key);

    static UiExplorerScreenCatalogCache disabled() {
        return new UiExplorerScreenCatalogCache() {
            @Override
            public Optional<UiExplorerScreenCatalog> find(Key key) {
                return Optional.empty();
            }

            @Override
            public void save(Key key, UiExplorerScreenCatalog catalog) {
                // No-op test/default cache.
            }

            @Override
            public void evict(Key key) {
                // No-op test/default cache.
            }
        };
    }

    record Key(
            String systemId,
            String systemLabel,
            String requestedRef,
            String gitLabGroup,
            String gitLabProjectName,
            String repositoryId,
            String projectPath,
            String searchMode,
            List<String> pathPrefixes,
            int maxRouteNodes,
            int maxRouteFiles,
            int maxSourceReads,
            int maxAliasResolutions,
            int maxImportDepth
    ) {

        public Key {
            pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
        }
    }
}
