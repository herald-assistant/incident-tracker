package pl.mkn.tdw.api.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.brokenCatalog;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.emptyCatalog;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.port;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.typicalCatalog;

class OperationalContextViewServiceTest {

    @Test
    void shouldReturnEmptySummaryForStarterTemplates() {
        var service = new OperationalContextViewService(port(emptyCatalog()));

        var summary = service.summary();

        assertEquals("ok", summary.catalogStatus());
        assertEquals(0, summary.systems());
        assertEquals(0, summary.repositories());
        assertEquals(0, summary.codeSearchScopes());
        assertEquals(0, summary.processes());
        assertEquals(0, summary.integrations());
    }

    @Test
    void shouldExposeCatalogueRowsFromSimplifiedContract() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        var system = service.systems().get(0);
        assertEquals("agreement-service", system.id());
        assertEquals("internal-application", system.kind());
        assertEquals("team-a", system.owner().value());
        assertTrue(system.relations().count() >= 4);
        assertEquals(1, system.signals().count());

        var repository = service.repositories().get(0);
        assertEquals("Group/agreement-service", repository.project());
        assertEquals("Group", repository.group());
        assertEquals("team-a", repository.owner().value());
        assertEquals(1, repository.systems().count());
        assertEquals(1, repository.contexts().count());
        assertEquals(1, repository.processes().count());
        assertTrue(repository.codeSearchScopes().count() >= 1);
        assertTrue(repository.codeSearchRoles().detailsIds().contains("primary in agreement-repo"));

        var codeSearchScope = service.codeSearchScopes().get(0);
        assertEquals("Agreement Service Scope", codeSearchScope.name());
        assertEquals(1, codeSearchScope.target().count());
        assertEquals(1, codeSearchScope.repositories().count());
        assertEquals(1, codeSearchScope.limitations().count());

        var process = service.processes().get(0);
        assertEquals("Business process for submitting an agreement.", process.purpose());
        assertEquals(1, process.systems().count());
        assertEquals(1, process.externalSystems().count());
        assertEquals(1, process.repositories().count());
        assertEquals(1, process.contexts().count());
        assertEquals(1, process.steps().count());

        var integration = service.integrations().get(0);
        assertEquals("agreement-service", integration.sourceSystem());
        assertEquals("partner-system", integration.targetSystems());
        assertEquals("external-handoff", integration.category());
        assertEquals("synchronous-request", integration.integrationStyle());
        assertEquals("outbound", integration.flowDirection());
        assertEquals(1, integration.processes().count());
        assertEquals(1, integration.contexts().count());

        var context = service.boundedContexts().get(0);
        assertEquals("Bounded context for agreement decisions.", context.purpose());
        assertEquals(1, context.systems().count());
        assertEquals(1, context.terms().count());

        var team = service.teams().get(0);
        assertEquals(1, team.ownsSystems().count());
        assertEquals(1, team.ownsRepositories().count());
        assertEquals(1, team.ownsProcesses().count());
        assertEquals(1, team.ownsContexts().count());
        assertEquals(1, team.ownsIntegrations().count());
    }

    @Test
    void shouldExposeOnlyRelationsAndCodeSearchReadModelsForOperatorApi() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        var relations = service.entityRelationsReadModel("system", "agreement-service");
        assertEquals("operational-context.entity-relations", relations.contract());
        assertEquals("agreement-service", relations.analysisTarget().id());
        assertTrue(relations.neighbors().stream().anyMatch(ref -> ref.id().equals("agreement-repo")));

        var codeSearch = service.codeSearchReadModel("system", "agreement-service");
        assertEquals("operational-context.code-search", codeSearch.contract());
        assertTrue(codeSearch.scopes().stream().anyMatch(scope -> scope.scope().id().equals("agreement-service-scope")));
        assertTrue(codeSearch.repositories().stream().anyMatch(repository -> repository.repository().id().equals("agreement-repo")));
        assertTrue(codeSearch.limitations().contains("Partner internals are outside this catalog."));
    }

    @Test
    void shouldExposeCompactProfilesWithoutRemovedExpansions() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        var compactEntity = (OperationalContextProfiledReadModelDto) service.entity(
                "system",
                "agreement-service",
                "default"
        );
        var compactRelations = (OperationalContextProfiledReadModelDto) service.entityRelationsReadModel(
                "system",
                "agreement-service",
                "default"
        );
        var compactCodeSearch = (OperationalContextProfiledReadModelDto) service.codeSearchReadModel(
                "system",
                "agreement-service",
                "default"
        );

        assertEquals("operational-context.entity-detail", compactEntity.contract());
        assertEquals(
                java.util.List.of("profile=expanded", "relations", "code-search"),
                compactEntity.availableExpansions()
        );

        assertEquals("operational-context.entity-relations", compactRelations.contract());
        assertEquals("default", compactRelations.profile());
        assertTrue(compactRelations.data().containsKey("neighbors"));

        assertEquals("operational-context.code-search", compactCodeSearch.contract());
        assertEquals("default", compactCodeSearch.profile());
        assertTrue(compactCodeSearch.suggestedTools().contains("gitlab_list_available_repositories"));
        assertTrue(compactCodeSearch.suggestedTools().contains("gitlab_search_repository_candidates"));
    }

    @Test
    void shouldValidateBrokenReferences() {
        var service = new OperationalContextViewService(port(brokenCatalog()));

        var findings = service.validation();

        assertTrue(findings.stream().anyMatch(finding ->
                finding.category().equals("UNKNOWN_RELATION_TARGET")
                        && finding.detail().contains("missing-system")));
    }

    @Test
    void shouldSearchByBusinessAndCatalogTerms() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        assertFalse(service.search("agreement-service").isEmpty());
        assertTrue(service.search("business-analysis").stream()
                .anyMatch(result -> result.type().equals("code-search-scope")));
        assertTrue(service.search("partner-handoff").stream()
                .anyMatch(result -> result.type().equals("integration")));
        assertTrue(service.search("Agreement").stream()
                .anyMatch(result -> result.type().equals("glossary-term")));
    }

    @Test
    void shouldReturnEntityDetailsAndControlledNotFound() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        var detail = service.entity("system", "agreement-service");

        assertEquals("system", detail.type());
        assertEquals("agreement-service", detail.id());
        assertFalse(detail.recognitionSignals().isEmpty());
        assertThrows(
                OperationalContextEntityNotFoundException.class,
                () -> service.entity("system", "missing")
        );
    }
}
