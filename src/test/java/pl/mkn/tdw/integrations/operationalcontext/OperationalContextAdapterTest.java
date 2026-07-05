package pl.mkn.tdw.integrations.operationalcontext;

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
        assertEquals(List.of("notification-provider"), catalog.integrations().get(0).participants().finalTargetSystems());
        assertEquals("server", catalog.integrations().get(0).participants().finalTargets().get(0).role());
        assertEquals(1, catalog.processes().size());
        assertEquals(2, catalog.repositories().size());
        assertEquals(1, catalog.codeSearchScopes().size());
        assertEquals(2, catalog.boundedContexts().size());
        assertEquals(2, catalog.teams().size());
        assertEquals(1, catalog.glossaryTerms().size());
        assertEquals(2, catalog.handoffRules().size());
        assertEquals(3, catalog.openQuestions().size());
        assertEquals("Confirm whether Customer Profile Context should include shared customer rule semantics.", catalog.openQuestions().get(0).question());
        assertEquals("bounded-context", catalog.openQuestions().get(0).entityType());
        assertEquals("customer-profile-context", catalog.openQuestions().get(0).entityId());
        assertEquals("crm-customer-service-code-search", catalog.codeSearchScopes().get(0).id());
        assertEquals(2, catalog.codeSearchScopes().get(0).repositories().size());
        assertEquals("supporting-library", catalog.codeSearchScopes().get(0).repositories().get(1).role());
        assertTrue(catalog.openQuestions().stream()
                .anyMatch(question -> question.sourceFile().equals("glossary.md")
                        && question.question().equals("Confirm whether remote API error terminology needs CRM-specific subtypes.")));
        assertTrue(catalog.openQuestions().stream()
                .anyMatch(question -> question.sourceFile().equals("handoff-rules.md")
                        && question.question().equals("Confirm ownership boundary evidence for notification-provider synchronous failures.")));
        assertFalse(catalog.indexDocument().isBlank());
    }

    @Test
    void shouldLoadTestCatalogWithoutIntegrationParticipantReferenceDuplication() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.integrations().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("DUPLICATED_PARTICIPANT_REFERENCE_SYSTEM")
                        || finding.code().equals("DUPLICATED_PARTICIPANT_REFERENCE_BOUNDED_CONTEXT")));
    }

    @Test
    void shouldLoadTestCatalogWithoutSystemSelfReferencesOrDerivedDependencyCopies() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.systems().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("SELF_REFERENCE")
                        || finding.code().equals("SYSTEM_DEPENDENCY_DERIVED_FROM_INTEGRATION")));
    }

    @Test
    void shouldLoadTestCatalogWithoutBoundedContextDerivedReferenceCopies() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.boundedContexts().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("BOUNDED_CONTEXT_SYSTEM_REFERENCE_DERIVED")
                        || finding.code().equals("BOUNDED_CONTEXT_INTEGRATION_REFERENCE_DERIVED")));
    }

    @Test
    void shouldLoadTestCatalogWithoutProcessParticipantSystemReferenceCopies() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.processes().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().equals("PROCESS_PARTICIPANT_REFERENCE_SYSTEM")));
    }

    @Test
    void shouldLoadTestCatalogWithoutCodeSearchTargetReferenceCopies() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertFalse(catalog.codeSearchScopes().isEmpty());
        assertTrue(findings.stream().noneMatch(finding ->
                finding.code().startsWith("CODE_SEARCH_TARGET_")
                        && finding.code().endsWith("_DERIVED_FROM_REPOSITORY")));
    }

    @Test
    void shouldLoadTestCatalogWithoutStrictValidationErrors() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);

        assertTrue(
                findings.stream().noneMatch(finding -> finding.severity().equals("error")),
                () -> "Test catalog should not contain strict validation errors: "
                        + findings.stream()
                                .filter(finding -> finding.severity().equals("error"))
                                .toList()
        );
    }

    @Test
    void shouldLoadTestCatalogWithoutUnknownRelationTargets() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var index = new OperationalContextRelationIndexBuilder().build(catalog);
        var unknownTargets = index.validationFindings().stream()
                .filter(finding -> finding.code().equals("UNKNOWN_RELATION_TARGET"))
                .toList();

        assertTrue(
                unknownTargets.isEmpty(),
                () -> "Test catalog should not contain unknown relation targets: " + unknownTargets
        );
    }

    @Test
    void shouldLoadTestCatalogWithoutBidirectionalReferences() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);
        var bidirectionalReferences = findings.stream()
                .filter(finding -> finding.code().equals("BIDIRECTIONAL_REFERENCE"))
                .toList();

        assertTrue(
                bidirectionalReferences.isEmpty(),
                () -> "Test catalog should not contain bidirectional references: " + bidirectionalReferences
        );
    }

    @Test
    void shouldLoadTestCatalogWithoutValidationWarnings() {
        var adapter = new OperationalContextAdapter(testProperties());

        var catalog = adapter.loadContext(OperationalContextQuery.all());
        var findings = new OperationalContextReadModelValidator().validate(catalog);
        var warnings = findings.stream()
                .filter(finding -> finding.severity().equals("warning"))
                .toList();

        assertTrue(
                warnings.isEmpty(),
                () -> "Test catalog should not contain validation warnings: " + warnings
        );
    }

    @Test
    void shouldFilterCatalogByEntryTypeAndExactValue() {
        var adapter = new OperationalContextAdapter(testProperties());
        var query = new OperationalContextQuery(
                Set.of(OperationalContextEntryType.SYSTEM),
                List.of(OperationalContextFilter.exact(OperationalContextEntryType.SYSTEM, "id", "crm-customer-service")),
                false
        );

        var catalog = adapter.loadContext(query);

        assertEquals(1, catalog.systems().size());
        assertEquals("crm-customer-service", catalog.systems().get(0).id());
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
                        "notification-sync"
                )),
                false
        );

        var catalog = adapter.loadContext(query);

        assertTrue(catalog.systems().isEmpty());
        assertEquals(1, catalog.glossaryTerms().size());
        assertEquals("remote-api-error", catalog.glossaryTerms().get(0).id());
    }

    private static OperationalContextProperties testProperties() {
        var properties = new OperationalContextProperties();
        properties.setResourceRoot("operational-context");
        return properties;
    }
}
