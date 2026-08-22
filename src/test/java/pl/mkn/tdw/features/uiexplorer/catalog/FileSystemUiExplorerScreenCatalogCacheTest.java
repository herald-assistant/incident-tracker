package pl.mkn.tdw.features.uiexplorer.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemUiExplorerScreenCatalogCacheTest {

    @TempDir
    Path workspaceDirectory;

    @Test
    void shouldReadAnonymizedCrmCatalogAfterCacheRecreation() {
        var key = key("crm-review");
        var catalog = catalog();

        cache().save(key, catalog);

        assertThat(cache().find(key)).contains(catalog);
    }

    @Test
    void shouldKeepCrmRefsIsolatedAndEvictOnlyTheRequestedEntry() {
        var reviewKey = key("crm-review");
        var releaseKey = key("crm-release");
        var cache = cache();
        cache.save(reviewKey, catalog());
        cache.save(releaseKey, catalog());

        cache.evict(reviewKey);

        assertThat(cache.find(reviewKey)).isEmpty();
        assertThat(cache.find(releaseKey)).contains(catalog());
    }

    private FileSystemUiExplorerScreenCatalogCache cache() {
        var properties = new LocalWorkspaceProperties();
        properties.setDirectory(workspaceDirectory.toString());
        var paths = new LocalWorkspacePaths(properties);
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        return new FileSystemUiExplorerScreenCatalogCache(
                properties,
                paths,
                new LocalWorkspaceJsonFileStore(objectMapper)
        );
    }

    private UiExplorerScreenCatalogCache.Key key(String ref) {
        return new UiExplorerScreenCatalogCache.Key(
                "crm-agent-portal",
                "CRM Agent Portal",
                ref,
                "crm",
                "agent-portal",
                "crm-agent-portal-repository",
                "crm/agent-portal",
                "path-prefixes",
                List.of("apps/crm-agent", "libs/crm-ui"),
                400,
                80,
                300,
                500,
                12
        );
    }

    private UiExplorerScreenCatalog catalog() {
        return new UiExplorerScreenCatalog(
                "crm-agent-portal",
                "CRM Agent Portal",
                new UiExplorerSourceRevision("crm-review", "crm-ui-revision-a1b2c3"),
                UiExplorerScreenCatalogStatus.READY,
                List.of(new UiExplorerScreenCatalogEntry(
                        "crm-contact-create",
                        "Create contact",
                        "/contacts/new",
                        "/contacts",
                        "RESOLVED",
                        true,
                        List.of("CrmAgentGuard"),
                        List.of(),
                        List.of()
                )),
                List.of(),
                List.of(),
                new UiExplorerScreenCatalogBoundary(2, 2, 9, 3, 0, false, 400, 80, 300, 500, 12)
        );
    }
}
