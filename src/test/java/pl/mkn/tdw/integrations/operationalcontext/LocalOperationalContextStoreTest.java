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
    void shouldBootstrapOneLocalCrmCopyAndKeepLocalChangesAfterRestart() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());

        var initial = store.loadOrBootstrap();

        assertEquals("tdw-data/operational-context", initial.readSnapshot().source());
        assertTrue(Files.isRegularFile(root.resolve("systems.yml")));
        Files.writeString(root.resolve("operational-context-index.md"), "# Local CRM catalogue\n");

        var restarted = store(root, new OperationalContextAtomicMover()).loadOrBootstrap();

        assertEquals("# Local CRM catalogue\n", restarted.rawDocuments().content("operational-context-index.md"));
        assertFalse(Files.exists(root.resolve("revisions")));
        assertFalse(Files.exists(root.resolve("manifest.json")));
    }

    @Test
    void shouldReplaceOnlyOneCrmDocumentWithoutCreatingHistory() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());
        var current = store.loadOrBootstrap();
        var systemsBefore = Files.readString(root.resolve("systems.yml"));
        var candidate = new LinkedHashMap<>(current.rawDocuments().contents());
        candidate.put("operational-context-index.md", "# Updated local CRM catalogue\n");

        var updated = store.publishCandidate(candidate);

        assertEquals("# Updated local CRM catalogue\n", updated.rawDocuments().content("operational-context-index.md"));
        assertEquals(systemsBefore, Files.readString(root.resolve("systems.yml")));
        assertFalse(Files.exists(root.resolve("revisions")));
    }

    @Test
    void shouldRejectAChangeSpanningMultipleCrmDocuments() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());
        var current = store.loadOrBootstrap();
        var candidate = new LinkedHashMap<>(current.rawDocuments().contents());
        candidate.put("operational-context-index.md", "# Changed CRM index\n");
        candidate.put("systems.yml", "# Changed anonymized CRM systems\nsystems: []\ngaps: []\n");

        var exception = assertThrows(
                OperationalContextStoreException.class,
                () -> store.publishCandidate(candidate)
        );

        assertEquals(OperationalContextStoreException.Code.INVALID_CANDIDATE, exception.code());
        assertEquals("# CRM operational context\n", Files.readString(root.resolve("operational-context-index.md")));
    }

    @Test
    void shouldKeepTheCrmDocumentUnchangedWhenAtomicReplacementFails() throws Exception {
        var root = temporaryDirectory.resolve("tdw-data").resolve("operational-context");
        var store = store(root, new OperationalContextAtomicMover());
        var current = store.loadOrBootstrap();
        var candidate = new LinkedHashMap<>(current.rawDocuments().contents());
        candidate.put("operational-context-index.md", "# Failed CRM update\n");
        var failingStore = store(root, new FailingDocumentMover());

        var exception = assertThrows(
                OperationalContextStoreException.class,
                () -> failingStore.publishCandidate(candidate)
        );

        assertEquals(OperationalContextStoreException.Code.LOCAL_COPY_UNAVAILABLE, exception.code());
        assertEquals("# CRM operational context\n", Files.readString(root.resolve("operational-context-index.md")));
        try (var files = Files.list(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    private LocalOperationalContextStore store(Path root, OperationalContextAtomicMover mover) {
        var properties = new OperationalContextProperties();
        properties.setStorageDirectory(root.toString());
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
        documents.put("operational-context-index.md", "# CRM operational context\n");
        return Map.copyOf(documents);
    }

    private static final class FailingDocumentMover extends OperationalContextAtomicMover {

        @Override
        void replaceFile(Path source, Path target) throws IOException {
            throw new IOException("Anonymous CRM filesystem failure");
        }
    }
}
