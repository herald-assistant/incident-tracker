package pl.mkn.incidenttracker.api.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCatalog;
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
        assertEquals(0, summary.processes());
        assertEquals(0, summary.integrations());
    }

    @Test
    void shouldExposeCatalogueRows() {
        var service = new OperationalContextViewService(port(sampleCatalog()));

        assertEquals(2, service.systems().size());
        assertEquals("app-core", service.systems().get(0).id());
        assertEquals(1, service.repositories().size());
        assertEquals("app-core-repo", service.repositories().get(0).id());
        assertEquals(1, service.processes().size());
        assertEquals(1, service.integrations().size());
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

        assertFalse(service.search("app-core").isEmpty());
        assertTrue(service.search("app-core-repo").stream().anyMatch(result -> result.type().equals("repository")));
        assertTrue(service.search("/api/resources").stream().anyMatch(result -> result.type().equals("system")));
        assertTrue(service.search("SOAP Fault").stream().anyMatch(result -> result.type().equals("glossary-term")));
    }

    @Test
    void shouldReturnEntityDetailsAndControlledNotFound() {
        var service = new OperationalContextViewService(port(sampleCatalog()));

        var detail = service.entity("system", "app-core");

        assertEquals("system", detail.type());
        assertEquals("app-core", detail.id());
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
        return new OperationalContextCatalog(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ""
        );
    }

    private static OperationalContextCatalog sampleCatalog() {
        return new OperationalContextCatalog(
                List.of(team("core-team", "Core Team", "app-core", "app-core-repo", "main-process", "core-context", "app-core-to-partner")),
                List.of(process("main-process", "Main Process", "core-team", List.of("app-core"), List.of("partner-service"), List.of("app-core-repo"), List.of("core-context"))),
                List.of(
                        system("app-core", "App Core", "internal", "core-team", List.of("app-core-repo"), List.of("main-process"), List.of("core-context"), List.of("app-core"), List.of("/api/resources")),
                        system("partner-service", "Partner Service", "external", "", List.of(), List.of("main-process"), List.of("core-context"), List.of("partner-service"), List.of("/partner/resource"))
                ),
                List.of(integration("app-core-to-partner", "app-core", "partner-service", "core-team", List.of("main-process"), List.of("core-context"))),
                List.of(repository("app-core-repo", "app-core-repo", "core-team", List.of("app-core"), List.of("main-process"), List.of("core-context"))),
                List.of(context("core-context", "Core Context", "core-team", List.of("app-core"), List.of("app-core-repo"), List.of("main-process"))),
                List.of(new OperationalContextCatalog.GlossaryTerm(
                        "soap-fault",
                        "SOAP Fault",
                        "integration-term",
                        "Error returned by a synchronous SOAP integration.",
                        List.of(),
                        List.of(),
                        List.of("SOAPFault", "api.partner.local"),
                        List.of("app-core-to-partner"),
                        List.of(),
                        List.of()
                )),
                List.of(new OperationalContextCatalog.HandoffRule(
                        "integration-failure",
                        "External integration failure",
                        "Core Team",
                        List.of("Evidence points to partner endpoint"),
                        List.of(),
                        List.of("correlationId", "endpoint"),
                        List.of("Verify external call"),
                        List.of("Core Team"),
                        List.of()
                )),
                List.of(),
                "index"
        );
    }

    private static OperationalContextCatalog brokenCatalog() {
        return new OperationalContextCatalog(
                List.of(team("core-team", "Core Team", "", "", "", "", "")),
                List.of(),
                List.of(
                        system("app-core", "App Core", "internal", "core-team", List.of("missing-repo"), List.of("missing-process"), List.of(), List.of("shared-service"), List.of("/api/shared")),
                        system("billing-core", "Billing Core", "internal", "", List.of(), List.of(), List.of(), List.of("shared-service"), List.of("/api/shared"))
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

    private static Map<String, Object> system(
            String id,
            String name,
            String type,
            String ownerTeamId,
            List<String> repos,
            List<String> processes,
            List<String> contexts,
            List<String> serviceNames,
            List<String> endpoints
    ) {
        return map(
                "id", id,
                "name", name,
                "type", type,
                "ownerTeamId", ownerTeamId,
                "repos", repos,
                "processes", processes,
                "contexts", contexts,
                "signals", map("serviceNames", serviceNames, "endpoints", endpoints),
                "handoff", map("target", ownerTeamId, "requiredEvidence", List.of("correlationId"))
        );
    }

    private static Map<String, Object> repository(
            String id,
            String project,
            String ownerTeamId,
            List<String> systems,
            List<String> processes,
            List<String> contexts
    ) {
        return map(
                "id", id,
                "project", project,
                "group", "example/platform",
                "ownerTeamId", ownerTeamId,
                "systems", systems,
                "processes", processes,
                "contexts", contexts,
                "modules", List.of(map("id", "core", "packages", List.of("com.example.app"), "classHints", List.of("AppController"))),
                "signals", map("projectNames", List.of(project), "packagePrefixes", List.of("com.example.app")),
                "handoff", map("target", ownerTeamId, "requiredEvidence", List.of("project", "filePath"))
        );
    }

    private static Map<String, Object> process(
            String id,
            String name,
            String ownerTeamId,
            List<String> systems,
            List<String> externalSystems,
            List<String> repos,
            List<String> contexts
    ) {
        return map(
                "id", id,
                "name", name,
                "ownerTeamId", ownerTeamId,
                "systems", systems,
                "externalSystems", externalSystems,
                "repos", repos,
                "contexts", contexts,
                "steps", List.of(map("id", "call-core", "name", "Call core")),
                "completionSignals", List.of("done")
        );
    }

    private static Map<String, Object> integration(
            String id,
            String from,
            String to,
            String ownerTeamId,
            List<String> processes,
            List<String> contexts
    ) {
        return map(
                "id", id,
                "name", "App Core -> Partner",
                "from", from,
                "to", to,
                "ownerTeamId", ownerTeamId,
                "protocol", "HTTP",
                "type", "sync",
                "processes", processes,
                "contexts", contexts,
                "signals", map("endpoints", List.of("/partner/resource")),
                "handoff", map("target", ownerTeamId, "requiredEvidence", List.of("endpoint"))
        );
    }

    private static Map<String, Object> context(
            String id,
            String name,
            String ownerTeamId,
            List<String> systems,
            List<String> repos,
            List<String> processes
    ) {
        return map(
                "id", id,
                "name", name,
                "ownerTeamId", ownerTeamId,
                "systems", systems,
                "repos", repos,
                "processes", processes,
                "terms", List.of("soap-fault")
        );
    }

    private static Map<String, Object> team(
            String id,
            String name,
            String system,
            String repo,
            String process,
            String context,
            String integration
    ) {
        return map(
                "id", id,
                "name", name,
                "owns", map(
                        "systems", values(system),
                        "repos", values(repo),
                        "processes", values(process),
                        "contexts", values(context),
                        "integrations", values(integration)
                ),
                "handoff", map("target", "tech-lead", "requiredEvidence", List.of("correlationId"))
        );
    }

    private static List<String> values(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private static Map<String, Object> map(Object... values) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
