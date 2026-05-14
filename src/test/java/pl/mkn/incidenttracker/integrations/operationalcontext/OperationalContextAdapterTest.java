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
    void shouldLoadRuntimeCatalogWithoutIntegrationParticipantReferenceDuplication() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.integrations().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("DUPLICATED_PARTICIPANT_REFERENCE_SYSTEM")
                        || finding.code().equals("DUPLICATED_PARTICIPANT_REFERENCE_BOUNDED_CONTEXT")));
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutSystemSelfReferencesOrDerivedDependencyCopies() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.systems().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("SELF_REFERENCE")
                        || finding.code().equals("SYSTEM_DEPENDENCY_DERIVED_FROM_INTEGRATION")));
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutBoundedContextDerivedReferenceCopies() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.boundedContexts().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("BOUNDED_CONTEXT_SYSTEM_REFERENCE_DERIVED")
                        || finding.code().equals("BOUNDED_CONTEXT_INTEGRATION_REFERENCE_DERIVED")));
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutProcessParticipantSystemReferenceCopies() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.processes().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("PROCESS_PARTICIPANT_REFERENCE_SYSTEM")));
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutCodeSearchTargetReferenceCopies() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.codeSearchScopes().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().startsWith("CODE_SEARCH_TARGET_")
                        && finding.code().endsWith("_DERIVED_FROM_REPOSITORY")));
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutTeamReferenceCopies() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.teams().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("TEAM_REFERENCE_DERIVED_FROM_RESPONSIBILITY")));
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutStrictValidationErrors() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertTrue(
                findings.stream().noneMatch(finding -> finding.severity().equals("error")),
                () -> "Runtime catalog should not contain strict validation errors: "
                        + findings.stream()
                                .filter(finding -> finding.severity().equals("error"))
                                .toList()
        );
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutUnknownRelationTargets() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var index = new OperationalContextRelationIndexBuilder().build(catalog);
        var unknownTargets = index.validationFindings().stream()
                .filter(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET"))
                .toList();

        assertTrue(
                unknownTargets.isEmpty(),
                () -> "Runtime catalog should not contain unknown relation targets: " + unknownTargets
        );
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutBidirectionalReferences() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);
        var bidirectionalReferences = findings.stream()
                .filter(finding -> finding.code().equals("BIDIRECTIONAL_REFERENCE"))
                .toList();

        assertTrue(
                bidirectionalReferences.isEmpty(),
                () -> "Runtime catalog should not contain bidirectional references: " + bidirectionalReferences
        );
    }

    @Test
    void shouldLoadRuntimeCatalogWithoutValidationWarnings() {
        var adapter = new OperationalContextAdapter(new OperationalContextProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);
        var warnings = findings.stream()
                .filter(finding -> finding.severity().equals("warning"))
                .toList();

        assertTrue(
                warnings.isEmpty(),
                () -> "Runtime catalog should not contain validation warnings: " + warnings
        );
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
