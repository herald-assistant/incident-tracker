package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextAdapter;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextMcpToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OperationalContextMcpTools tools = new OperationalContextMcpTools(
            ignored -> catalog(),
            new OperationalContextToolMapper()
    );

    @Test
    void shouldExposeScopeCountsWithoutEntityDetails() {
        var result = tools.getScope("Sprawdzam zakres katalogu.", null);

        assertTrue(result.enabled());
        assertEquals("default", result.affordances().profile());
        assertTrue(result.affordances().suggestedTools().contains("opctx_search"));
        assertEquals(9, result.entityTypes().size());
        assertEquals(2, count(result, "system"));
        assertEquals(1, count(result, "repository"));
        assertEquals(1, count(result, "codeSearchScope"));
        assertEquals(1, count(result, "process"));
        assertEquals(1, count(result, "boundedContext"));
        assertEquals(1, count(result, "glossaryTerm"));
    }

    @Test
    void shouldListEntitiesAlphabeticallyWithPaginationAndSimpleFilter() {
        var firstPage = tools.listEntities(
                "system",
                1,
                1,
                null,
                "Przegladam systemy z katalogu.",
                null
        );

        assertEquals(2, firstPage.totalItems());
        assertEquals(2, firstPage.totalPages());
        assertTrue(firstPage.truncated());
        assertEquals("default", firstPage.affordances().profile());
        assertTrue(firstPage.affordances().links().stream()
                .anyMatch(link -> link.rel().equals("first-entity") && link.tool().equals("opctx_get_entity")));
        assertEquals("catalog", firstPage.items().get(0).id());

        var filtered = tools.listEntities(
                "system",
                1,
                20,
                "notifications-api",
                "Filtruje system po nazwie serwisu.",
                null
        );

        assertEquals(1, filtered.totalItems());
        assertEquals("notifications", filtered.items().get(0).id());
        assertTrue(filtered.items().get(0).facets().get("repositoryIds").contains("notifications-service"));
    }

    @Test
    void shouldSearchRankExactIdentityAndReturnMatchExplanation() {
        var result = tools.search(
                "notifications-api",
                List.of("system", "boundedContext"),
                8,
                "Dopasowuje sygnal z logow do katalogu.",
                null
        );

        assertFalse(result.truncated());
        assertEquals("notifications", result.results().get(0).id());
        assertEquals("system", result.results().get(0).type());
        assertTrue(result.results().get(0).confidence() >= 0.9);
        assertTrue(result.results().get(0).matchedFields().contains("identity"));
        assertTrue(result.results().get(0).matchedSignals().contains("notifications-api"));
        assertTrue(result.results().get(0).why().contains("system:notifications"));
        assertEquals("default", result.affordances().profile());
        assertTrue(result.affordances().links().stream()
                .anyMatch(link -> link.rel().equals("top-result") && link.tool().equals("opctx_get_entity")));
    }

    @Test
    void shouldReturnSystemDetailWithRelationsSignalsCodeSearchHandoffAndOpenQuestions() {
        var result = tools.getEntity(
                "system",
                "notifications",
                List.of("overview", "relations", "signals", "codeSearch", "handoff", "sourceCoverage", "openQuestions"),
                "Pobieram szczegoly systemu.",
                null
        );

        assertEquals("Notifications", result.label());
        assertEquals("default", result.affordances().profile());
        assertTrue(result.affordances().availableExpansions().contains("include=codeSearch"));
        assertTrue(result.affordances().suggestedNextReads().stream()
                .anyMatch(read -> read.contains("include=[codeSearch]")));
        assertEquals("high", result.overview().get("criticality"));
        assertTrue(result.relations().containsKey("references"));
        assertTrue(result.signals().containsKey("deployment"));
        assertFalse(result.signals().toString().contains("endpointPrefixes"));
        assertFalse(result.signals().toString().contains("/notifications"));
        assertTrue(result.codeSearch().containsKey("codeSearchScopes"));
        assertFalse(result.codeSearch().containsKey("localCodeSearchScope"));
        assertTrue(result.handoff().containsKey("requiredEvidence"));
        assertEquals(1, result.openQuestions().size());
        assertTrue(result.sourceRefs().contains("systems.yml#notifications"));
        assertFalse(result.overview().containsKey("payload"));
        assertFalse(result.relations().containsKey("rawSourcePreview"));
        assertFalse(result.toString().contains("rawSourcePreview"));
    }

    @Test
    void shouldCompactDefaultEntityToolPayloadAndExposeTruncation() {
        var result = tools.getEntity(
                "repository",
                "notifications-service",
                null,
                "Pobieram domyslny kompaktowy opis repozytorium.",
                null
        );

        assertEquals("default", result.affordances().profile());
        assertTrue(result.affordances().truncation().truncated());
        assertTrue(result.affordances().omittedBecause().stream()
                .anyMatch(reason -> reason.contains("compacted")));
        assertTrue(result.affordances().suggestedTools().contains("opctx_search"));
        assertFalse(result.toString().contains("rawSourcePreview"));
        assertFalse(result.toString().contains("payload"));
    }

    @Test
    void shouldKeepRuntimeToolEntityPayloadCompactForLargeCodeSearchScope() throws Exception {
        var runtimeTools = new OperationalContextMcpTools(
                new OperationalContextAdapter(new OperationalContextProperties()),
                new OperationalContextToolMapper()
        );

        var result = runtimeTools.getEntity(
                "codeSearchScope",
                "crm-customer-service-code-search",
                null,
                "Sprawdzam kompaktowy payload narzedzia dla duzego scope.",
                null
        );
        var json = objectMapper.writeValueAsString(result);

        assertEquals("default", result.affordances().profile());
        assertTrue(result.affordances().truncation().truncated());
        assertTrue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 100_000);
        assertFalse(json.contains("rawSourcePreview"));
        assertFalse(json.contains("\"payload\""));
        assertTrue(result.affordances().suggestedNextReads().stream()
                .anyMatch(read -> read.contains("include=[codeSearch]")));
    }

    @Test
    void shouldReturnDetailsForBoundedContextGlossaryTermAndCodeSearchScope() {
        var boundedContext = tools.getEntity(
                "boundedContext",
                "notifications",
                List.of("overview", "relations", "signals"),
                "Sprawdzam bounded context.",
                null
        );
        assertEquals("Notifications context", boundedContext.label());
        assertTrue(boundedContext.relations().containsKey("references"));
        assertTrue(boundedContext.signals().containsKey("operationalSignals"));

        var glossaryTerm = tools.getEntity(
                "glossaryTerm",
                "authorization",
                List.of("overview", "signals"),
                "Sprawdzam termin slownikowy.",
                null
        );
        assertEquals("Authorization", glossaryTerm.label());
        assertTrue(glossaryTerm.overview().get("definition").toString().contains("notification delivery"));
        assertTrue(glossaryTerm.signals().containsKey("synonyms"));

        var codeSearchScope = tools.getEntity(
                "codeSearchScope",
                "notifications-runtime",
                List.of("relations", "codeSearch", "sourceCoverage"),
                "Sprawdzam zakres szukania kodu.",
                null
        );
        assertEquals("Notifications runtime code scope", codeSearchScope.label());
        assertTrue(codeSearchScope.relations().containsKey("repositories"));
        assertTrue(codeSearchScope.codeSearch().containsKey("database"));
        assertTrue(codeSearchScope.sourceCoverage().containsKey("limitations"));
        assertFalse(codeSearchScope.codeSearch().containsKey("payload"));

        var integration = tools.getEntity(
                "integration",
                "notification-gateway-api",
                List.of("relations"),
                "Sprawdzam role uczestnikow integracji.",
                null
        );
        var participants = (Map<?, ?>) integration.relations().get("participants");
        assertTrue(participants.get("finalTargets").toString().contains("role=server"));
    }

    private int count(
            OperationalContextToolDtos.OpctxScopeResult result,
            String type
    ) {
        return result.entityTypes().stream()
                .filter(summary -> type.equals(summary.type()))
                .findFirst()
                .orElseThrow()
                .count();
    }

    private OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(team()),
                List.of(process()),
                List.of(system("catalog", "Catalog", "catalog-api"), system("notifications", "Notifications", "notifications-api")),
                List.of(integration()),
                List.of(repository()),
                List.of(codeSearchScope()),
                List.of(boundedContext()),
                List.of(glossaryTerm()),
                List.of(handoffRule()),
                List.of(openQuestion()),
                ""
        );
    }

    private Map<String, Object> team() {
        return map(
                "id", "notifications-team",
                "name", "Notifications Team",
                "summary", "Owns the notifications capability.",
                "references", map("systems", List.of("notifications"))
        );
    }

    private Map<String, Object> process() {
        return map(
                "id", "checkout",
                "name", "Checkout",
                "summary", "Customer checkout process.",
                "participants", map(
                        "primarySystems", List.of("notifications"),
                        "externalSystems", List.of("notification-gateway")
                ),
                "references", map(
                        "systems", List.of("notifications"),
                        "boundedContexts", List.of("notifications")
                ),
                "failureModes", List.of("Notification delivery timeout")
        );
    }

    private Map<String, Object> system(String id, String name, String serviceName) {
        return map(
                "id", id,
                "name", name,
                "criticality", id.equals("notifications") ? "high" : "medium",
                "summary", name + " system.",
                "aliases", List.of(serviceName),
                "references", map(
                        "repositories", id.equals("notifications") ? List.of("notifications-service") : List.of(),
                        "processes", id.equals("notifications") ? List.of("checkout") : List.of(),
                        "boundedContexts", id.equals("notifications") ? List.of("notifications") : List.of(),
                        "teams", id.equals("notifications") ? List.of("notifications-team") : List.of()
                ),
                "responsibilities", id.equals("notifications")
                        ? List.of(map("teamId", "notifications-team", "role", "owner"))
                        : List.of(),
                "matchSignals", map(
                        "strong", map(
                                "serviceNames", List.of(serviceName),
                                "endpointPrefixes", id.equals("notifications") ? List.of("/notifications") : List.of("/catalog")
                        )
                ),
                "deployment", map("serviceNames", List.of(serviceName)),
                "codeSearchScope", id.equals("notifications")
                        ? map(
                        "repositories", List.of("notifications-service"),
                        "packagePrefixes", List.of("pl.example.notifications"),
                        "classHints", List.of("NotificationController")
                )
                        : map(),
                "handoffHints", id.equals("notifications")
                        ? map(
                        "defaultRoute", "Notifications Team",
                        "requiredEvidence", List.of("correlationId", "provider response code")
                )
                        : map()
        );
    }

    private Map<String, Object> integration() {
        return map(
                "id", "notification-gateway-api",
                "name", "Notification Gateway API",
                "summary", "External notification delivery gateway.",
                "participants", map(
                        "source", map("system", "notifications"),
                        "targets", List.of(map("system", "notification-gateway", "externalOwner", "Provider")),
                        "finalTargets", List.of(map("system", "notification-gateway", "role", "server"))
                ),
                "transport", map("http", map("endpointPrefixes", List.of("/authorize"))),
                "implementation", map("classHints", List.of("NotificationGatewayClient"))
        );
    }

    private Map<String, Object> repository() {
        return map(
                "id", "notifications-service",
                "name", "Notifications Service",
                "repositoryType", "service",
                "summary", "Runtime implementation of notifications.",
                "git", map(
                        "provider", "gitlab",
                        "group", "platform",
                        "project", "notifications-service",
                        "projectPath", "platform/notifications-service",
                        "aliases", List.of("notifications-api")
                ),
                "references", map(
                        "systems", List.of("notifications"),
                        "boundedContexts", List.of("notifications"),
                        "processes", List.of("checkout"),
                        "integrations", List.of("notification-gateway-api")
                ),
                "matchSignals", map(
                        "strong", map(
                                "packagePrefixes", List.of("pl.example.notifications"),
                                "classHints", List.of(
                                        "NotificationController",
                                        "NotificationService",
                                        "NotificationRepository",
                                        "NotificationGatewayClient",
                                        "NotificationEventPublisher",
                                        "NotificationConfiguration",
                                        "NotificationScheduler",
                                        "NotificationEntity",
                                        "NotificationCommandHandler",
                                        "NotificationQueryHandler"
                                )
                        )
                )
        );
    }

    private Map<String, Object> codeSearchScope() {
        return map(
                "id", "notifications-runtime",
                "name", "Notifications runtime code scope",
                "scopeType", "system",
                "lifecycleStatus", "active",
                "target", map("type", "system", "id", "notifications"),
                "useFor", List.of("incident-analysis", "code-search"),
                "repositories", List.of(map(
                        "repoId", "notifications-service",
                        "role", "primary-implementation",
                        "priority", 1,
                        "moduleIds", List.of("app"),
                        "reason", "Primary implementation."
                )),
                "hints", map(
                        "packagePrefixes", List.of("pl.example.notifications"),
                        "classHints", List.of("NotificationController"),
                        "endpointHints", List.of("/notifications"),
                        "database", map("schemas", List.of("NOTIFICATIONS_APP"), "entities", List.of("NotificationEntity"))
                ),
                "limitations", List.of("Generated clients are partial.")
        );
    }

    private Map<String, Object> boundedContext() {
        return map(
                "id", "notifications",
                "name", "Notifications context",
                "summary", "Notification delivery context.",
                "references", map(
                        "systems", List.of("notifications"),
                        "repositories", List.of("notifications-service"),
                        "terms", List.of("authorization")
                ),
                "operationalSignals", map(
                        "serviceNames", List.of("notifications-api"),
                        "endpointPrefixes", List.of("/notifications"),
                        "packagePrefixes", List.of("pl.example.notifications")
                )
        );
    }

    private OperationalContextGlossaryTerm glossaryTerm() {
        return new OperationalContextGlossaryTerm(
                "authorization",
                "Authorization",
                "notifications",
                "External notification delivery before acknowledgement.",
                List.of("checkout"),
                List.of("authentication"),
                List.of("authorization", "notification delivery"),
                List.of("notifications"),
                List.of("authz"),
                List.of()
        );
    }

    private OperationalContextHandoffRule handoffRule() {
        return new OperationalContextHandoffRule(
                "notification-gateway-timeout",
                "Notification gateway timeout",
                "notifications-team",
                List.of("Timeout from notification gateway"),
                List.of("Local validation failure"),
                List.of("correlationId"),
                List.of("Check provider status."),
                List.of("platform-team"),
                List.of()
        );
    }

    private OperationalContextOpenQuestion openQuestion() {
        return new OperationalContextOpenQuestion(
                "open-question-notifications-owner",
                "systems.yml",
                "system",
                "notifications",
                "Confirm fallback owner for provider outages.",
                "warning",
                "open"
        );
    }

    private Map<String, Object> map(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < keyValues.length; index += 2) {
            map.put(keyValues[index].toString(), keyValues[index + 1]);
        }
        return map;
    }
}
