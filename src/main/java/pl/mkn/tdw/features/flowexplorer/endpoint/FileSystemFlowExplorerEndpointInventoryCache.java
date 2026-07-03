package pl.mkn.tdw.features.flowexplorer.endpoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse;
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
public class FileSystemFlowExplorerEndpointInventoryCache implements FlowExplorerEndpointInventoryCache {

    private static final String SCHEMA = "tdw.flow-explorer-endpoint-inventory-cache";
    private static final int VERSION = 1;

    private final LocalWorkspaceProperties properties;
    private final LocalWorkspacePaths paths;
    private final LocalWorkspaceJsonFileStore jsonFileStore;

    @Override
    public Optional<FlowExplorerEndpointInventoryResponse> find(Key key) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        return jsonFileStore.read(cacheFile(key), FlowExplorerEndpointInventoryCacheEntry.class)
                .filter(entry -> SCHEMA.equals(entry.schema()))
                .filter(entry -> entry.version() == VERSION)
                .filter(entry -> key.equals(entry.key()))
                .map(FlowExplorerEndpointInventoryCacheEntry::response);
    }

    @Override
    public void save(Key key, FlowExplorerEndpointInventoryResponse response) {
        if (!properties.isEnabled() || response == null || response.scannedRepositoryCount() <= 0) {
            return;
        }

        try {
            jsonFileStore.writeAtomic(
                    cacheFile(key),
                    new FlowExplorerEndpointInventoryCacheEntry(
                            SCHEMA,
                            VERSION,
                            Instant.now(),
                            key,
                            response
                    )
            );
        } catch (RuntimeException exception) {
            log.warn("Failed to write Flow Explorer endpoint inventory cache key={} error={}", key, exception.getMessage());
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
            log.warn("Failed to evict Flow Explorer endpoint inventory cache key={} error={}", key, exception.getMessage());
        }
    }

    private Path cacheFile(Key key) {
        return paths.root()
                .resolve("flow-explorer")
                .resolve("endpoint-inventory-cache")
                .resolve(cacheKeyHash(key) + ".json");
    }

    private String cacheKeyHash(Key key) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = String.join("\n",
                    safe(key.systemId()),
                    safe(key.requestedBranch()),
                    safe(key.resolvedRef()),
                    safe(key.gitLabGroup()),
                    String.join("|", key.repositories()),
                    safe(key.endpointPathPrefix()),
                    safe(key.httpMethod())
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

record FlowExplorerEndpointInventoryCacheEntry(
        String schema,
        int version,
        Instant cachedAt,
        FlowExplorerEndpointInventoryCache.Key key,
        FlowExplorerEndpointInventoryResponse response
) {
}
