package pl.mkn.tdw.api.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.brokenCatalog;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.emptyCatalog;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.map;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.port;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.typicalCatalog;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextValidationTestCreator.create;

class OperationalContextViewServiceTest {

    @Test
    void shouldReturnEmptySummaryForStarterTemplates() {
        var service = new OperationalContextViewService(port(emptyCatalog()), create());

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
        var service = new OperationalContextViewService(port(typicalCatalog()), create());

        var system = service.systems().get(0);
        assertEquals("crm-consent-service", system.id());
        assertEquals("internal-application", system.kind());
        assertEquals("team-a", system.owner().value());
        assertEquals(1, system.repositories().count());
        assertTrue(system.relations().count() >= 4);
        assertEquals(1, system.signals().count());

        var repository = service.repositories().get(0);
        assertEquals("Group/crm-consent-service", repository.project());
        assertEquals("Group", repository.group());
        assertEquals("team-a", repository.owner().value());
        assertEquals(1, repository.systems().count());
        assertEquals(1, repository.contexts().count());
        assertEquals(1, repository.processes().count());
        assertTrue(repository.codeSearchScopes().count() >= 1);
        assertTrue(repository.codeSearchRoles().detailsIds().contains("primary in crm-consent-repo"));

        var codeSearchScope = service.codeSearchScopes().get(0);
        assertEquals("CRM Consent Service Scope", codeSearchScope.name());
        assertEquals(1, codeSearchScope.target().count());
        assertEquals(1, codeSearchScope.repositories().count());
        assertEquals(1, codeSearchScope.limitations().count());

        var process = service.processes().get(0);
        assertEquals("Business process for capturing customer consent.", process.purpose());
        assertEquals(1, process.systems().count());
        assertEquals(1, process.externalSystems().count());
        assertEquals(1, process.repositories().count());
        assertEquals(1, process.contexts().count());
        assertEquals(1, process.steps().count());

        var integration = service.integrations().get(0);
        assertEquals("crm-consent-service", integration.sourceSystem());
        assertEquals("consent-registry", integration.targetSystems());
        assertEquals("external-handoff", integration.category());
        assertEquals("synchronous-request", integration.integrationStyle());
        assertEquals("outbound", integration.flowDirection());
        assertEquals(1, integration.processes().count());
        assertEquals(1, integration.contexts().count());

        var context = service.boundedContexts().get(0);
        assertEquals("Bounded context for customer consent decisions.", context.purpose());
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
        var service = new OperationalContextViewService(port(typicalCatalog()), create());

        var relations = service.entityRelationsReadModel("system", "crm-consent-service");
        assertEquals("operational-context.entity-relations", relations.contract());
        assertEquals("crm-consent-service", relations.analysisTarget().id());
        assertTrue(relations.neighbors().stream().anyMatch(ref -> ref.id().equals("crm-consent-repo")));

        var codeSearch = service.codeSearchReadModel("system", "crm-consent-service");
        assertEquals("operational-context.code-search", codeSearch.contract());
        assertTrue(codeSearch.scopes().stream().anyMatch(scope -> scope.scope().id().equals("crm-consent-service-scope")));
        assertTrue(codeSearch.repositories().stream().anyMatch(repository -> repository.repository().id().equals("crm-consent-repo")));
        assertTrue(codeSearch.limitations().contains("Consent registry internals are outside this catalog."));
    }

    @Test
    void shouldExposeCompactProfilesWithoutRemovedExpansions() {
        var service = new OperationalContextViewService(port(typicalCatalog()), create());

        var compactEntity = (OperationalContextProfiledReadModelDto) service.entity(
                "system",
                "crm-consent-service",
                "default"
        );
        var compactRelations = (OperationalContextProfiledReadModelDto) service.entityRelationsReadModel(
                "system",
                "crm-consent-service",
                "default"
        );
        var compactCodeSearch = (OperationalContextProfiledReadModelDto) service.codeSearchReadModel(
                "system",
                "crm-consent-service",
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
        var service = new OperationalContextViewService(port(brokenCatalog()), create());

        var findings = service.validation();

        assertTrue(findings.stream().anyMatch(finding ->
                finding.category().equals("UNKNOWN_RELATION_TARGET")
                        && finding.detail().contains("missing-system")));
    }

    @Test
    void shouldExposeOwnershipContractValidation() {
        var service = new OperationalContextViewService(port(OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "ownership", map("ownerLabel", "legacy repository owner")
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        )), create());

        var findings = service.validation();

        assertTrue(findings.stream().anyMatch(finding ->
                finding.category().equals("OWNERSHIP_OUTSIDE_SYSTEM_OR_BOUNDED_CONTEXT")
                        && finding.entityType().equals("repository")));
    }

    @Test
    void shouldSearchByBusinessAndCatalogTerms() {
        var service = new OperationalContextViewService(port(typicalCatalog()), create());

        assertFalse(service.search("crm-consent-service").isEmpty());
        assertTrue(service.search("business-analysis").stream()
                .anyMatch(result -> result.type().equals("code-search-scope")));
        assertTrue(service.search("consent-registry-handoff").stream()
                .anyMatch(result -> result.type().equals("integration")));
        assertTrue(service.search("Customer Consent").stream()
                .anyMatch(result -> result.type().equals("glossary-term")));
    }

    @Test
    void shouldReturnEntityDetailsAndControlledNotFound() {
        var service = new OperationalContextViewService(port(typicalCatalog()), create());

        var detail = service.entity("system", "crm-consent-service");
        var repositoryDetail = service.entity("repository", "crm-consent-repo");
        var contextDetail = service.entity("bounded-context", "customer-consent-context");

        assertEquals("system", detail.type());
        assertEquals("crm-consent-service", detail.id());
        assertFalse(detail.recognitionSignals().isEmpty());
        assertTrue(detail.overviewSections().stream().anyMatch(section ->
                section.title().equals("System runtime and responsibility")
                        && section.fields().get("configurationDirectory").equals("crm/consent-service")
                        && section.fields().get("externalOwner").equals("CRM managed platform provider")
        ));
        assertTrue(repositoryDetail.overviewSections().stream().anyMatch(section ->
                section.title().equals("Evidence")
                        && section.fields().toString().contains("crm/consent-service/pom.xml")
        ));
        assertTrue(repositoryDetail.overviewSections().stream().anyMatch(section ->
                section.title().equals("AI exploration guidance")
                        && section.fields().toString().contains("CRM consent validation")
        ));
        assertTrue(contextDetail.overviewSections().stream().anyMatch(section ->
                section.title().equals("Local language and scope")
                        && section.fields().toString().contains("CRM contact consent decisions")
        ));
        assertTrue(contextDetail.overviewSections().stream().anyMatch(section ->
                section.title().equals("Semantic boundary")
                        && section.fields().toString().contains("ConsentPreferenceRecorded")
        ));
        assertTrue(contextDetail.overviewSections().stream().anyMatch(section ->
                section.title().equals("Evidence")
                        && section.fields().toString().contains("Anonymized CRM consent glossary")
        ));
        assertTrue(contextDetail.overviewSections().stream().anyMatch(section ->
                section.title().equals("AI exploration guidance")
                        && section.fields().toString().contains("ConsentPreference")
        ));
        assertThrows(
                OperationalContextEntityNotFoundException.class,
                () -> service.entity("system", "missing")
        );
    }
}
