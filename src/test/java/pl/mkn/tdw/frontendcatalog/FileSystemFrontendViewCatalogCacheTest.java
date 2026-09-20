package pl.mkn.tdw.frontendcatalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemFrontendViewCatalogCacheTest {
    @TempDir Path workspaceDirectory;

    @Test
    void shouldShareAnonymizedCrmViewCatalogAndRefreshOnlyTheSelectedScope() {
        var review = key("crm-review");
        var release = key("crm-release");
        var cache = cache();

        cache.save(review, catalog("crm-review", "crm-revision-review"));
        cache.save(release, catalog("crm-release", "crm-revision-release"));

        assertThat(cache().find(review)).contains(catalog("crm-review", "crm-revision-review"));
        cache.evict(review);
        assertThat(cache.find(review)).isEmpty();
        assertThat(cache.find(release)).contains(catalog("crm-release", "crm-revision-release"));
    }

    @Test
    void shouldIgnoreAnOlderCrmViewCatalogCacheVersion() throws Exception {
        var review = key("crm-review");
        var cache = cache();
        cache.save(review, catalog("crm-review", "crm-revision-review"));
        Path cacheFile;
        try (var files = Files.list(workspaceDirectory.resolve("frontend-catalog").resolve("view-cache"))) {
            cacheFile = files.findFirst().orElseThrow();
        }
        var mapper = new ObjectMapper().findAndRegisterModules();
        var entry = (ObjectNode) mapper.readTree(cacheFile.toFile());
        entry.put("version", 1);
        mapper.writeValue(cacheFile.toFile(), entry);

        assertThat(cache().find(review)).isEmpty();
    }

    private FileSystemFrontendViewCatalogCache cache() {
        var properties = new LocalWorkspaceProperties();
        properties.setDirectory(workspaceDirectory.toString());
        var paths = new LocalWorkspacePaths(properties);
        return new FileSystemFrontendViewCatalogCache(properties, paths,
                new LocalWorkspaceJsonFileStore(new ObjectMapper().findAndRegisterModules()));
    }

    private FrontendViewCatalogCache.Key key(String ref) {
        return new FrontendViewCatalogCache.Key("crm-agent-portal", "CRM Agent Portal", ref,
                "crm", "agent-portal", "crm-agent-portal-repository", "crm/agent-portal",
                "path-prefixes", List.of("apps/crm-agent"), 400, 80, 300, 500, 12);
    }

    private FrontendViewCatalog catalog(String branch, String revision) {
        return new FrontendViewCatalog("crm-agent-portal", "CRM Agent Portal",
                new FrontendViewCatalog.SourceRevision(branch, revision), FrontendViewCatalog.Status.READY,
                List.of(new FrontendViewCatalog.View("crm-contact-create", "Create contact", "/contacts/new",
                        "/contacts", "RESOLVED", false, List.of(), List.of(), List.of())),
                List.of(), List.of(),
                new FrontendViewCatalog.Boundary(2, 2, 9, 3, 0, false, 400, 80, 300, 500, 12));
    }
}
