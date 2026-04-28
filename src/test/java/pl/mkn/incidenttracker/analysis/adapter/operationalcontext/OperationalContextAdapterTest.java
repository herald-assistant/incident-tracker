package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

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
        assertEquals(1, catalog.processes().size());
        assertEquals(2, catalog.repositories().size());
        assertEquals(1, catalog.boundedContexts().size());
        assertEquals(2, catalog.teams().size());
        assertEquals(1, catalog.glossaryTerms().size());
        assertEquals(2, catalog.handoffRules().size());
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
        assertEquals("app-core", OperationalContextMaps.text(catalog.systems().get(0), "id"));
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
