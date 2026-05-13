package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextAdapterTest {

    @Test
    void shouldLoadFullCatalogWhenQueryIsUnfiltered() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());

        assertEquals(2, catalog.systems().size());
        assertEquals(1, catalog.integrations().size());
        assertEquals(List.of("partner-service"), catalog.integrations().get(0).participants().finalTargetSystems());
        assertEquals("server", catalog.integrations().get(0).participants().finalTargets().get(0).role());
        assertEquals(1, catalog.processes().size());
        assertEquals(2, catalog.repositories().size());
        assertEquals(1, catalog.codeSearchScopes().size());
        assertEquals(1, catalog.boundedContexts().size());
        assertEquals(2, catalog.teams().size());
        assertEquals(1, catalog.glossaryTerms().size());
        assertEquals(2, catalog.handoffRules().size());
        assertEquals(3, catalog.openQuestions().size());
        assertEquals("Confirm whether Core Context should include shared library semantics.", catalog.openQuestions().get(0).question());
        assertEquals("bounded-context", catalog.openQuestions().get(0).entityType());
        assertEquals("core-context", catalog.openQuestions().get(0).entityId());
        assertEquals("app-core-code-search", catalog.codeSearchScopes().get(0).id());
        assertEquals(2, catalog.codeSearchScopes().get(0).repositories().size());
        assertEquals("shared-library", catalog.codeSearchScopes().get(0).repositories().get(1).role());
        assertTrue(catalog.openQuestions().stream()
                .anyMatch(question -> question.sourceFile().equals("glossary.md")
                        && question.question().equals("Confirm whether SOAP fault terminology needs separate domain-specific subtypes.")));
        assertTrue(catalog.openQuestions().stream()
                .anyMatch(question -> question.sourceFile().equals("handoff-rules.md")
                        && question.question().equals("Confirm actual routing target for partner-service synchronous failures.")));
        assertFalse(catalog.indexDocument().isBlank());
    }

    @Test
    void shouldFilterCatalogByEntryTypeAndExactValue() {
        var adapter = new OperationalContextAdapter(testProperties());
        var query = new OperationalContextQuery(
                Set.of(OperationalContextEntryType.SYSTEM),
                List.of(OperationalContextFilter.exact(OperationalContextEntryType.SYSTEM, "id", "app-core")),
                false
        );

        var catalog = adapter.loadContext(query);

        assertEquals(1, catalog.systems().size());
        assertEquals("app-core", catalog.systems().get(0).id());
        assertTrue(catalog.integrations().isEmpty());
        assertTrue(catalog.processes().isEmpty());
        assertTrue(catalog.repositories().isEmpty());
        assertTrue(catalog.boundedContexts().isEmpty());
        assertTrue(catalog.teams().isEmpty());
        assertTrue(catalog.glossaryTerms().isEmpty());
        assertTrue(catalog.handoffRules().isEmpty());
        assertTrue(catalog.indexDocument().isBlank());
    }

    @Test
    void shouldFilterGlossaryTermsWithContainsMode() {
        var adapter = new OperationalContextAdapter(testProperties());
        var query = new OperationalContextQuery(
                Set.of(OperationalContextEntryType.GLOSSARY_TERM),
                List.of(OperationalContextFilter.contains(
                        OperationalContextEntryType.GLOSSARY_TERM,
                        "canonicalReferences",
                        "partner-sync"
                )),
                false
        );

        var catalog = adapter.loadContext(query);

        assertTrue(catalog.systems().isEmpty());
        assertEquals(1, catalog.glossaryTerms().size());
        assertEquals("soap-fault", catalog.glossaryTerms().get(0).id());
    }

    private static OperationalContextProperties testProperties() {
        var properties = new OperationalContextProperties();
        properties.setResourceRoot("operational-context-test");
        return properties;
    }
}
