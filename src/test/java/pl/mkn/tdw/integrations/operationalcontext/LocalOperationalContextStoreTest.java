package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOperationalContextStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldBootstrapEnabledLocalCopyAtStartup() {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover(), true);

        store.initializeLocalCopyAtStartup();

        assertTrue(Files.isRegularFile(root.resolve("systems.yml")));
        assertFalse(Files.exists(root.resolve("operational-context-index.md")));
    }

    @Test
    void shouldNotBootstrapDisabledLocalCopyAtStartup() {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover(), false);

        store.initializeLocalCopyAtStartup();

        assertFalse(Files.exists(root));
    }

    @Test
    void shouldBootstrapOneLocalCrmCopyAndKeepLocalChangesAfterRestart() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());

        var initial = store.loadOrBootstrap();

        assertEquals("tdw-data/operational-context", initial.readSnapshot().source());
        assertTrue(Files.isRegularFile(root.resolve("systems.yml")));
        var localSystems = "systems: []\ngaps: []\n# Local CRM catalogue\n";
        Files.writeString(root.resolve("systems.yml"), localSystems);

        var restarted = store(root, new OperationalContextAtomicMover()).loadOrBootstrap();

        assertEquals(localSystems, restarted.rawDocuments().content("systems.yml"));
        assertFalse(Files.exists(root.resolve("revisions")));
        assertFalse(Files.exists(root.resolve("manifest.json")));
    }

    @Test
    void shouldLoadExistingLocalCopyWithOrphanedIndexWithoutIncludingItInSnapshot() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        store(root, new OperationalContextAtomicMover()).loadOrBootstrap();
        var orphanedIndex = "# Operator-edited legacy index\n";
        Files.writeString(root.resolve("operational-context-index.md"), orphanedIndex);
        var localSystems = "systems:\n  - id: crm-local-service\n    name: CRM Local Service\n    systemType: internal-service\ngaps: []\n";
        Files.writeString(root.resolve("systems.yml"), localSystems);

        var restarted = store(root, new OperationalContextAtomicMover()).loadOrBootstrap();

        assertEquals(localSystems, restarted.rawDocuments().content("systems.yml"));
        assertEquals("crm-local-service", restarted.readSnapshot().catalog().systems().get(0).id());
        assertFalse(restarted.rawDocuments().contents().containsKey("operational-context-index.md"));
        assertEquals(orphanedIndex, Files.readString(root.resolve("operational-context-index.md")));
    }

    @Test
    void shouldReplaceOnlyOneCrmDocumentWithoutCreatingHistory() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());
        var current = store.loadOrBootstrap();
        var teamsBefore = Files.readString(root.resolve("teams.yml"));
        var candidate = new LinkedHashMap<>(current.rawDocuments().contents());
        var updatedSystems = "systems: []\ngaps: []\n# Updated local CRM catalogue\n";
        candidate.put("systems.yml", updatedSystems);

        var updated = store.publishCandidate(candidate);

        assertEquals(updatedSystems, updated.rawDocuments().content("systems.yml"));
        assertEquals(teamsBefore, Files.readString(root.resolve("teams.yml")));
        assertFalse(Files.exists(root.resolve("revisions")));
    }

    @Test
    void shouldRejectAChangeSpanningMultipleCrmDocuments() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());
        var current = store.loadOrBootstrap();
        var candidate = new LinkedHashMap<>(current.rawDocuments().contents());
        candidate.put("teams.yml", "teams: []\ngaps: []\n# Changed CRM teams\n");
        candidate.put("systems.yml", "systems: []\ngaps: []\n# Changed CRM systems\n");

        var exception = assertThrows(
                OperationalContextStoreException.class,
                () -> store.publishCandidate(candidate)
        );

        assertEquals(OperationalContextStoreException.Code.INVALID_CANDIDATE, exception.code());
        assertEquals("teams: []\ngaps: []\n", Files.readString(root.resolve("teams.yml")));
        assertEquals("systems: []\ngaps: []\n", Files.readString(root.resolve("systems.yml")));
    }

    @Test
    void shouldKeepTheCrmDocumentUnchangedWhenAtomicReplacementFails() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());
        var current = store.loadOrBootstrap();
        var candidate = new LinkedHashMap<>(current.rawDocuments().contents());
        candidate.put("systems.yml", "systems: []\ngaps: []\n# Failed CRM update\n");
        var failingStore = store(root, new FailingDocumentMover());

        var exception = assertThrows(
                OperationalContextStoreException.class,
                () -> failingStore.publishCandidate(candidate)
        );

        assertEquals(OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE, exception.code());
        assertEquals("systems: []\ngaps: []\n", Files.readString(root.resolve("systems.yml")));
        try (var files = Files.list(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private LocalOperationalContextStore store(Path root, OperationalContextAtomicMover mover) {
        return store(root, mover, true);
    }

    private LocalOperationalContextStore store(
            Path root,
            OperationalContextAtomicMover mover,
            boolean enabled
    ) {
        var properties = new OperationalContextProperties();
        properties.setStorageDirectory(root.toString());
        properties.setEnabled(enabled);
        OperationalContextDocumentSource seed = () -> new OperationalContextRawDocuments("crm-seed", seedDocuments());
        var validation = new OperationalContextCatalogValidationService(() ->
                new OperationalContextValidationBaseline(
                        1,
                        OperationalContextCatalogValidationService.FINGERPRINT_ALGORITHM,
                        List.of()
                )
        );
        return new LocalOperationalContextStore(
                properties,
                seed,
                new OperationalContextCatalogCodec(),
                mover,
                validation
        );
    }

    private Map<String, String> seedDocuments() {
        var documents = new LinkedHashMap<String, String>();
        documents.put("teams.yml", "teams: []\ngaps: []\n");
        documents.put("processes.yml", "processes: []\ngaps: []\n");
        documents.put("systems.yml", "systems: []\ngaps: []\n");
        documents.put("integrations.yml", "integrations: []\ngaps: []\n");
        documents.put("repo-map.yml", "repositories: []\ngaps: []\n");
        documents.put("code-search-scopes.yml", "codeSearchScopes: []\ngaps: []\n");
        documents.put("bounded-contexts.yml", "boundedContexts: []\ngaps: []\n");
        documents.put("glossary.yml", "terms: []\ngaps: []\n");
        documents.put("handoff-rules.yml", "handoffRules: []\ngaps: []\n");
        return Map.copyOf(documents);
    }

    private static final class FailingDocumentMover extends OperationalContextAtomicMover {

        @Override
        void replaceFile(Path source, Path target) throws IOException {
            throw new IOException("Anonymous CRM filesystem failure");
        }
    }
}
