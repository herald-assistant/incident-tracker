package pl.mkn.tdw.features.flowexplorer.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.features.flowexplorer.api.FlowExplorerEndpointInventoryResponse;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemFlowExplorerEndpointInventoryCacheTest {

    @TempDir
    Path workspaceDirectory;

    @Test
    void shouldReadEndpointInventoryAfterCacheRecreation() {
        var key = new FlowExplorerEndpointInventoryCache.Key(
                "catalog-core",
                null,
                "main",
                "platform/backend",
                List.of("catalog-api:catalog-api:platform/backend/catalog-api"),
                null,
                null
        );
        var response = response();

        cache().save(key, response);

        var recreatedCache = cache();
        var cached = recreatedCache.find(key);

        assertTrue(cached.isPresent());
        assertEquals(response, cached.get());
    }

    @Test
    void shouldEvictEndpointInventoryCacheEntry() {
        var key = new FlowExplorerEndpointInventoryCache.Key(
                "catalog-core",
                null,
                "main",
                "platform/backend",
                List.of("catalog-api:catalog-api:platform/backend/catalog-api"),
                null,
                null
        );
        var cache = cache();
        cache.save(key, response());

        cache.evict(key);

        assertTrue(cache.find(key).isEmpty());
    }

    private FileSystemFlowExplorerEndpointInventoryCache cache() {
        var properties = new LocalWorkspaceProperties();
        properties.setDirectory(workspaceDirectory.toString());
        var paths = new LocalWorkspacePaths(properties);
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        return new FileSystemFlowExplorerEndpointInventoryCache(
                properties,
                paths,
                new LocalWorkspaceJsonFileStore(objectMapper)
        );
    }

    private FlowExplorerEndpointInventoryResponse response() {
        return new FlowExplorerEndpointInventoryResponse(
                "catalog-core",
                null,
                "main",
                "platform/backend",
                null,
                null,
                1,
                1,
                0,
                2,
                2,
                false,
                Instant.parse("2026-06-18T10:00:00Z"),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
