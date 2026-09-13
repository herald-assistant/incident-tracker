package pl.mkn.tdw.features.operationalcontextassistance.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDocumentSnapshot;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalContextAssistanceCatalogMaterialServiceTest {

    @Test
    void capturesAllDocumentsAndOnlyApplicablePackagedInstructions() {
        var port = mock(OperationalContextPort.class);
        var catalog = OperationalContextCatalog.empty();
        var documents = documents();
        documents.put("systems.yml", Map.of("systems", java.util.List.of(Map.of(
                "id", "crm-contact-core", "summary", "Known fact from active runtime storage"
        ))));
        when(port.currentDocumentSnapshot()).thenReturn(new OperationalContextDocumentSnapshot(
                "runtime-digest", "tdw-data/operational-context", catalog, documents
        ));

        var material = new OperationalContextAssistanceCatalogMaterialService(port, new ObjectMapper()).capture();

        assertEquals("runtime-digest", material.contentDigest());
        assertSame(catalog, material.catalog());
        assertEquals(documents, material.documents());
        assertEquals(11, material.guidance().size());
        assertTrue(material.guidance().get("operational-context-fill-order.md").contains("Fact ownership"));
        assertTrue(material.guidance().get("operational-context-field-guidance.md").contains("Strict maintenance validation"));
        assertFalse(material.guidance().containsKey("cleanup-operational-context-report.md"));
        assertFalse(material.guidance().keySet().stream().anyMatch(name -> name.endsWith(".ps1")));
    }

    @Test
    void blocksOversizedCatalogInsteadOfSilentlyTruncatingIt() {
        var port = mock(OperationalContextPort.class);
        var documents = documents();
        documents.put("systems.yml", Map.of("systems", "x".repeat(
                OperationalContextAssistanceCatalogMaterialService.MAX_CATALOG_BYTES
        )));
        when(port.currentDocumentSnapshot()).thenReturn(new OperationalContextDocumentSnapshot(
                "large-digest", "tdw-data/operational-context", OperationalContextCatalog.empty(), documents
        ));

        var exception = assertThrows(OperationalContextAssistanceMaterialException.class,
                () -> new OperationalContextAssistanceCatalogMaterialService(port, new ObjectMapper()).capture());

        assertEquals("OPCTX_ASSISTANCE_MATERIAL_UNAVAILABLE", exception.code());
        assertTrue(exception.getMessage().contains("pełnego katalogu"));
    }

    @Test
    void blocksIncompleteDocumentSet() {
        var port = mock(OperationalContextPort.class);
        when(port.currentDocumentSnapshot()).thenReturn(new OperationalContextDocumentSnapshot(
                "incomplete", "tdw-data/operational-context", OperationalContextCatalog.empty(),
                Map.of("systems.yml", Map.of())
        ));

        assertThrows(OperationalContextAssistanceMaterialException.class,
                () -> new OperationalContextAssistanceCatalogMaterialService(port, new ObjectMapper()).capture());
    }

    private static Map<String, Map<String, Object>> documents() {
        var documents = new LinkedHashMap<String, Map<String, Object>>();
        for (var name : java.util.List.of(
                "systems.yml", "repo-map.yml", "code-search-scopes.yml", "processes.yml",
                "bounded-contexts.yml", "integrations.yml", "teams.yml", "glossary.yml", "handoff-rules.yml"
        )) {
            documents.put(name, Map.of());
        }
        return documents;
    }
}
