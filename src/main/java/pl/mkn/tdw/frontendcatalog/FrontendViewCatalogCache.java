package pl.mkn.tdw.frontendcatalog;

import java.util.List;
import java.util.Optional;

public interface FrontendViewCatalogCache {

    Optional<FrontendViewCatalog> find(Key key);

    void save(Key key, FrontendViewCatalog catalog);

    void evict(Key key);

    static FrontendViewCatalogCache disabled() {
        return new FrontendViewCatalogCache() {
            @Override
            public Optional<FrontendViewCatalog> find(Key key) {
                return Optional.empty();
            }

            @Override
            public void save(Key key, FrontendViewCatalog catalog) {
                // No-op cache for isolated unit tests.
            }

            @Override
            public void evict(Key key) {
                // No-op cache for isolated unit tests.
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
