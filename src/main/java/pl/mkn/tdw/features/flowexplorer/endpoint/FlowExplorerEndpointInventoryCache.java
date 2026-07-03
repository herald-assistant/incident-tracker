package pl.mkn.tdw.features.flowexplorer.endpoint;

import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse;

import java.util.List;
import java.util.Optional;

public interface FlowExplorerEndpointInventoryCache {

    Optional<FlowExplorerEndpointInventoryResponse> find(Key key);

    void save(Key key, FlowExplorerEndpointInventoryResponse response);

    void evict(Key key);

    static FlowExplorerEndpointInventoryCache disabled() {
        return new FlowExplorerEndpointInventoryCache() {
            @Override
            public Optional<FlowExplorerEndpointInventoryResponse> find(Key key) {
                return Optional.empty();
            }

            @Override
            public void save(Key key, FlowExplorerEndpointInventoryResponse response) {
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
            String requestedBranch,
            String resolvedRef,
            String gitLabGroup,
            List<String> repositories,
            String endpointPathPrefix,
            String httpMethod
    ) {

        public Key {
            repositories = repositories != null ? List.copyOf(repositories) : List.of();
        }
    }
}
