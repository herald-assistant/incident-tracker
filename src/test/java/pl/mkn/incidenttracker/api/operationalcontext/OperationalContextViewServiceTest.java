package pl.mkn.incidenttracker.api.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextPort;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextViewServiceTest {

    @Test
    void shouldReturnEmptySummaryForStarterTemplates() {
        var service = new OperationalContextViewService(port(emptyCatalog()));

        var summary = service.summary();

        assertEquals("empty", summary.catalogStatus());
        assertEquals(0, summary.systems());
        assertEquals(0, summary.repositories());
        assertEquals(0, summary.codeSearchScopes());
        assertEquals(0, summary.processes());
        assertEquals(0, summary.integrations());
    }

    @Test
    void shouldExposeCatalogueRows() {
        var service = new OperationalContextViewService(port(sampleCatalog()));

        assertEquals(2, service.systems().size());
        assertEquals("new-system", service.systems().get(0).id());
        assertEquals(1, service.repositories().size());
        assertEquals("new-repo", service.repositories().get(0).id());
        assertEquals(1, service.codeSearchScopes().size());
        assertEquals("new-scope", service.codeSearchScopes().get(0).id());
        assertEquals(1, service.processes().size());
        assertEquals(1, service.integrations().size());
    }

    @Test
    void shouldExposeRowsFromCurrentOperationalContextContract() {
        var service = new OperationalContextViewService(port(currentContractCatalog()));

        var system = service.systems().get(0);
        assertEquals("new-system", system.id());
        assertEquals("internal-application", system.kind());
        assertEquals("team-a", system.owner().value());
        assertEquals(1, system.repositories().count());
        assertEquals(1, system.processes().count());
        assertEquals(1, system.contexts().count());
        assertEquals(1, system.signals().count());

        var repository = service.repositories().get(0);
        assertEquals("Group/new-service", repository.project());
        assertEquals("Group", repository.group());
        assertEquals("team-a", repository.owner().value());
        assertEquals(1, repository.systems().count());
        assertEquals(1, repository.processes().count());
        assertEquals(1, repository.contexts().count());
        assertFalse(repository.packageRoots().detailsIds().isEmpty());
        assertFalse(repository.entrypoints().detailsIds().isEmpty());
        assertEquals(1, repository.codeSearchScopes().count());
        assertEquals(1, repository.codeSearchRoles().count());

        var codeSearchScope = service.codeSearchScopes().get(0);
        assertEquals("New Scope", codeSearchScope.name());
        assertEquals(1, codeSearchScope.repositories().count());
        assertEquals(3, codeSearchScope.targets().count());

        var process = service.processes().get(0);
        assertEquals("Current process summary", process.purpose());
        assertEquals(1, process.systems().count());
        assertEquals(1, process.externalSystems().count());
        assertEquals(1, process.repositories().count());
        assertEquals(1, process.contexts().count());
        assertEquals(1, process.steps().count());
        assertEquals(1, process.completionSignals().count());

        var integration = service.integrations().get(0);
        assertEquals("new-system", integration.sourceSystem());
        assertEquals("partner-system", integration.targetSystems());
        assertEquals("REST", integration.protocols());
        assertEquals("synchronous-request", integration.integrationStyle());
        assertEquals(1, integration.processes().count());
        assertEquals(1, integration.contexts().count());

        var context = service.boundedContexts().get(0);
        assertEquals("Current bounded context summary", context.purpose());
        assertEquals(1, context.systems().count());
        assertEquals(1, context.repositories().count());
        assertEquals(1, context.processes().count());
        assertEquals(1, context.terms().count());

        var team = service.teams().get(0);
        assertEquals(1, team.ownsSystems().count());
        assertEquals(1, team.ownsRepositories().count());
        assertEquals(1, team.ownsProcesses().count());
        assertEquals(1, team.ownsContexts().count());
        assertEquals(1, team.ownsIntegrations().count());

        assertTrue(service.search("NewController").stream().anyMatch(result -> result.type().equals("repository")));
        assertTrue(service.search("primary-application").stream().anyMatch(result -> result.type().equals("code-search-scope")));
        assertTrue(service.search("/new/api").stream().anyMatch(result -> result.type().equals("integration")));
    }

    @Test
    void shouldValidateBrokenReferencesDuplicateSignalsAndOwnerConsistency() {
        var service = new OperationalContextViewService(port(brokenCatalog()));

        var findings = service.validation();

        assertTrue(findings.stream().anyMatch(finding ->
                finding.category().equals("reference-integrity")
                        && finding.entityType().equals("system")
                        && finding.detail().contains("missing-repo")));
        assertTrue(findings.stream().anyMatch(finding ->
                finding.category().equals("signal-quality")
                        && finding.title().contains("Duplicate serviceName")));
        assertTrue(findings.stream().anyMatch(finding ->
                finding.category().equals("ownership-consistency")
                        && finding.title().contains("Owner team does not list")));
    }

    @Test
    void shouldSearchByServiceProjectEndpointAndTerm() {
        var service = new OperationalContextViewService(port(sampleCatalog()));

        assertFalse(service.search("new-system").isEmpty());
        assertTrue(service.search("NewController").stream().anyMatch(result -> result.type().equals("repository")));
        assertTrue(service.search("/new/api").stream().anyMatch(result -> result.type().equals("integration")));
        assertTrue(service.search("New Term").stream().anyMatch(result -> result.type().equals("glossary-term")));
    }

    @Test
    void shouldReturnEntityDetailsAndControlledNotFound() {
        var service = new OperationalContextViewService(port(sampleCatalog()));

        var detail = service.entity("system", "new-system");

        assertEquals("system", detail.type());
        assertEquals("new-system", detail.id());
        assertFalse(detail.recognitionSignals().isEmpty());
        assertThrows(
                OperationalContextEntityNotFoundException.class,
                () -> service.entity("system", "missing")
        );
    }

    private static OperationalContextPort port(OperationalContextCatalog catalog) {
        return query -> catalog;
    }

    private static OperationalContextCatalog emptyCatalog() {
        return OperationalContextCatalog.empty();
    }

    private static OperationalContextCatalog sampleCatalog() {
        return currentContractCatalog();
    }

    private static OperationalContextCatalog brokenCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "core-team",
                        "name", "Core Team",
                        "references", map(
                                "systems", List.of(),
                                "repositories", List.of(),
                                "processes", List.of(),
                                "boundedContexts", List.of(),
                                "integrations", List.of()
                        )
                )),
                List.of(),
                List.of(
                        map(
                                "id", "app-core",
                                "name", "App Core",
                                "kind", "internal-application",
                                "references", map("repositories", List.of("missing-repo"), "processes", List.of("missing-process")),
                                "responsibilities", List.of(map("teamId", "core-team")),
                                "matchSignals", map("exact", map("serviceNames", List.of("shared-service"), "endpointPrefixes", List.of("/api/shared")))
                        ),
                        map(
                                "id", "billing-core",
                                "name", "Billing Core",
                                "kind", "internal-application",
                                "matchSignals", map("exact", map("serviceNames", List.of("shared-service"), "endpointPrefixes", List.of("/api/shared")))
                        )
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static OperationalContextCatalog currentContractCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(map(
                        "id", "team-a",
                        "name", "Team A",
                        "purpose", "Owns current-contract sample.",
                        "references", map(
                                "systems", List.of("new-system"),
                                "repositories", List.of("new-repo"),
                                "processes", List.of("new-process"),
                                "boundedContexts", List.of("new-context"),
                                "integrations", List.of("new-integration")
                        ),
                        "responsibilities", List.of(
                                map("targetType", "system", "targetId", "new-system", "teamId", "team-a"),
                                map("targetType", "repository", "targetId", "new-repo", "teamId", "team-a"),
                                map("targetType", "process", "targetId", "new-process", "teamId", "team-a"),
                                map("targetType", "bounded-context", "targetId", "new-context", "teamId", "team-a"),
                                map("targetType", "integration", "targetId", "new-integration", "teamId", "team-a")
                        ),
                        "handoffHints", map(
                                "defaultRouteLabel", "Team A",
                                "requiredEvidence", List.of("correlationId")
                        )
                )),
                List.of(map(
                        "id", "new-process",
                        "name", "New Process",
                        "summary", "Current process summary",
                        "participants", map(
                                "primarySystems", List.of("new-system"),
                                "externalSystems", List.of("partner-system")
                        ),
                        "references", map(
                                "repositories", List.of("new-repo"),
                                "boundedContexts", List.of("new-context"),
                                "teams", List.of("team-a")
                        ),
                        "responsibilities", List.of(map("teamId", "team-a")),
                        "processSteps", List.of(map("id", "step-1", "name", "Run step")),
                        "processBoundary", map("endsWhen", List.of("finished")),
                        "handoffHints", map("defaultRouteLabel", "Team A", "requiredEvidence", List.of("correlationId"))
                )),
                List.of(
                        map(
                                "id", "new-system",
                                "name", "New System",
                                "kind", "internal-application",
                                "purpose", "Current system purpose",
                                "references", map(
                                        "repositories", List.of("new-repo"),
                                        "processes", List.of("new-process"),
                                        "boundedContexts", List.of("new-context"),
                                        "teams", List.of("team-a")
                                ),
                                "responsibilities", List.of(map("teamId", "team-a")),
                                "matchSignals", map("exact", map("serviceNames", List.of("new-service"))),
                                "handoffHints", map("defaultRouteLabel", "Team A", "requiredEvidence", List.of("correlationId"))
                        ),
                        map(
                                "id", "partner-system",
                                "name", "Partner System",
                                "kind", "external-system",
                                "matchSignals", map("exact", map("serviceNames", List.of("partner-service"))),
                                "handoffHints", map("defaultRouteLabel", "Partner", "requiredEvidence", List.of("endpoint"))
                        )
                ),
                List.of(map(
                        "id", "new-integration",
                        "name", "New System to Partner",
                        "summary", "Current integration summary",
                        "integrationStyle", "synchronous-request",
                        "participants", map(
                                "source", map("system", "new-system"),
                                "targets", List.of(map("system", "partner-system")),
                                "finalTargets", List.of("partner-system")
                        ),
                        "transport", map("protocols", List.of("REST"), "http", map("endpointPrefixes", List.of("/new/api"))),
                        "references", map(
                                "processes", List.of("new-process"),
                                "boundedContexts", List.of("new-context"),
                                "teams", List.of("team-a")
                        ),
                        "responsibilities", List.of(map("teamId", "team-a")),
                        "matchSignals", map("strong", map("endpointPrefixes", List.of("/new/api"))),
                        "handoffHints", map("defaultRouteLabel", "Team A", "requiredEvidence", List.of("endpoint"))
                )),
                List.of(map(
                        "id", "new-repo",
                        "name", "New Repo",
                        "git", map("group", "Group", "project", "new-service", "projectPath", "Group/new-service"),
                        "references", map(
                                "systems", List.of("new-system"),
                                "processes", List.of("new-process"),
                                "boundedContexts", List.of("new-context")
                        ),
                        "sourceLayout", map("sourceRoots", List.of("src/main/java/com/example/newservice")),
                        "matchSignals", map("strong", map("packagePrefixes", List.of("com.example.newservice"), "classHints", List.of("NewController"))),
                        "modules", List.of(map("moduleId", "api", "sourceRoots", List.of("src/main/java"), "matchSignals", map("strong", map("classHints", List.of("NewController"))))),
                        "handoffHints", map("defaultRouteLabel", "Team A", "requiredEvidence", List.of("projectPath"))
                )),
                List.of(map(
                        "id", "new-scope",
                        "name", "New Scope",
                        "lifecycleStatus", "active",
                        "target", map(
                                "systems", List.of("new-system"),
                                "processes", List.of("new-process"),
                                "boundedContexts", List.of("new-context")
                        ),
                        "useFor", List.of("incident-analysis"),
                        "repositories", List.of(map(
                                "repoId", "new-repo",
                                "role", "primary-application",
                                "priority", 1,
                                "include", true,
                                "moduleIds", List.of("api"),
                                "reason", "Main code path for new-system."
                        )),
                        "packagePrefixes", List.of("com.example.newservice"),
                        "classHints", List.of("NewController"),
                        "endpointHints", List.of("/new/api"),
                        "databaseHints", map("schemas", List.of("NEW_SCHEMA"), "tables", List.of("NEW_TABLE")),
                        "workflowHints", map("workflowNames", List.of("NewWorkflow")),
                        "searchStrategy", map(
                                "priorityOrder", List.of("primary-application"),
                                "includeGeneratedClients", false,
                                "includeSharedLibraries", true
                        )
                )),
                List.of(map(
                        "id", "new-context",
                        "name", "New Context",
                        "summary", "Current bounded context summary",
                        "references", map(
                                "systems", List.of("new-system"),
                                "repositories", List.of("new-repo"),
                                "processes", List.of("new-process"),
                                "terms", List.of("new-term"),
                                "teams", List.of("team-a")
                        ),
                        "responsibilities", List.of(map("teamId", "team-a")),
                        "matchSignals", map("exact", map("terms", List.of("new-term"))),
                        "handoffHints", map("defaultRouteLabel", "Team A", "requiredEvidence", List.of("bounded context"))
                )),
                List.of(new OperationalContextGlossaryTerm(
                        "new-term",
                        "New Term",
                        "domain-term",
                        "A term from current contract.",
                        List.of(),
                        List.of(),
                        List.of("new-term"),
                        List.of("new-context"),
                        List.of(),
                        List.of()
                )),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> map(Object... values) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
