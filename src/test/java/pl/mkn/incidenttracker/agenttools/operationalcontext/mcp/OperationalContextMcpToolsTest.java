package pl.mkn.incidenttracker.agenttools.operationalcontext.mcp;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextGlossaryTerm;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextHandoffRule;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextOpenQuestion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextMcpToolsTest {

    private final OperationalContextMcpTools tools = new OperationalContextMcpTools(
            ignored -> catalog(),
            new OperationalContextToolMapper()
    );

    @Test
    void shouldExposeScopeCountsWithoutEntityDetails() {
        var result = tools.getScope("Sprawdzam zakres katalogu.", null);

        assertTrue(result.enabled());
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
        assertEquals("billing", firstPage.items().get(0).id());

        var filtered = tools.listEntities(
                "system",
                1,
                20,
                "payments-api",
                "Filtruje system po nazwie serwisu.",
                null
        );

        assertEquals(1, filtered.totalItems());
        assertEquals("payments", filtered.items().get(0).id());
        assertTrue(filtered.items().get(0).facets().get("repositoryIds").contains("payments-service"));
    }

    @Test
    void shouldSearchRankExactIdentityAndReturnMatchExplanation() {
        var result = tools.search(
                "payments-api",
                List.of("system", "boundedContext"),
                8,
                "Dopasowuje sygnal z logow do katalogu.",
                null
        );

        assertFalse(result.truncated());
        assertEquals("payments", result.results().get(0).id());
        assertEquals("system", result.results().get(0).type());
        assertTrue(result.results().get(0).confidence() >= 0.9);
        assertTrue(result.results().get(0).matchedFields().contains("identity"));
        assertTrue(result.results().get(0).matchedSignals().contains("payments-api"));
        assertTrue(result.results().get(0).why().contains("system:payments"));
    }

    @Test
    void shouldReturnSystemDetailWithRelationsSignalsCodeSearchHandoffAndOpenQuestions() {
        var result = tools.getEntity(
                "system",
                "payments",
                List.of("overview", "relations", "signals", "codeSearch", "handoff", "sourceCoverage", "openQuestions"),
                "Pobieram szczegoly systemu.",
                null
        );

        assertEquals("Payments", result.label());
        assertEquals("high", result.overview().get("criticality"));
        assertTrue(result.relations().containsKey("references"));
        assertTrue(result.signals().containsKey("deployment"));
        assertTrue(result.codeSearch().containsKey("codeSearchScopes"));
        assertTrue(result.handoff().containsKey("requiredEvidence"));
        assertEquals(1, result.openQuestions().size());
        assertTrue(result.sourceRefs().contains("systems.yml#payments"));
        assertFalse(result.overview().containsKey("payload"));
        assertFalse(result.relations().containsKey("rawSourcePreview"));
    }

    @Test
    void shouldReturnDetailsForBoundedContextGlossaryTermAndCodeSearchScope() {
        var boundedContext = tools.getEntity(
                "boundedContext",
                "payments",
                List.of("overview", "relations", "signals"),
                "Sprawdzam bounded context.",
                null
        );
        assertEquals("Payments context", boundedContext.label());
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
        assertTrue(glossaryTerm.overview().get("definition").toString().contains("payment approval"));
        assertTrue(glossaryTerm.signals().containsKey("synonyms"));

        var codeSearchScope = tools.getEntity(
                "codeSearchScope",
                "payments-runtime",
                List.of("relations", "codeSearch", "sourceCoverage"),
                "Sprawdzam zakres szukania kodu.",
                null
        );
        assertEquals("Payments runtime code scope", codeSearchScope.label());
        assertTrue(codeSearchScope.relations().containsKey("repositories"));
        assertTrue(codeSearchScope.codeSearch().containsKey("databaseHints"));
        assertTrue(codeSearchScope.sourceCoverage().containsKey("limitations"));
        assertFalse(codeSearchScope.codeSearch().containsKey("payload"));
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
                List.of(system("billing", "Billing", "billing-api"), system("payments", "Payments", "payments-api")),
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
                "id", "payments-team",
                "name", "Payments Team",
                "summary", "Owns the payments capability.",
                "references", map("systems", List.of("payments"))
        );
    }

    private Map<String, Object> process() {
        return map(
                "id", "checkout",
                "name", "Checkout",
                "summary", "Customer checkout process.",
                "participants", map(
                        "primarySystems", List.of("payments"),
                        "externalSystems", List.of("payment-provider")
                ),
                "references", map(
                        "systems", List.of("payments"),
                        "boundedContexts", List.of("payments")
                ),
                "failureModes", List.of("Payment authorization timeout")
        );
    }

    private Map<String, Object> system(String id, String name, String serviceName) {
        return map(
                "id", id,
                "name", name,
                "criticality", id.equals("payments") ? "high" : "medium",
                "summary", name + " system.",
                "aliases", List.of(serviceName),
                "references", map(
                        "repositories", id.equals("payments") ? List.of("payments-service") : List.of(),
                        "processes", id.equals("payments") ? List.of("checkout") : List.of(),
                        "boundedContexts", id.equals("payments") ? List.of("payments") : List.of(),
                        "teams", id.equals("payments") ? List.of("payments-team") : List.of()
                ),
                "responsibilities", id.equals("payments")
                        ? List.of(map("teamId", "payments-team", "role", "owner"))
                        : List.of(),
                "matchSignals", map(
                        "strong", map(
                                "serviceNames", List.of(serviceName),
                                "endpointPrefixes", id.equals("payments") ? List.of("/payments") : List.of("/billing")
                        )
                ),
                "deployment", map("serviceNames", List.of(serviceName)),
                "codeSearchScope", id.equals("payments")
                        ? map(
                        "repositories", List.of("payments-service"),
                        "packagePrefixes", List.of("pl.example.payments"),
                        "classHints", List.of("PaymentController")
                )
                        : map(),
                "handoffHints", id.equals("payments")
                        ? map(
                        "defaultRoute", "Payments Team",
                        "requiredEvidence", List.of("correlationId", "provider response code")
                )
                        : map()
        );
    }

    private Map<String, Object> integration() {
        return map(
                "id", "payment-provider-api",
                "name", "Payment Provider API",
                "summary", "External payment authorization provider.",
                "participants", map(
                        "source", map("system", "payments"),
                        "targets", List.of(map("system", "payment-provider", "externalOwner", "Provider"))
                ),
                "transport", map("http", map("endpointPrefixes", List.of("/authorize"))),
                "implementation", map("classHints", List.of("PaymentProviderClient"))
        );
    }

    private Map<String, Object> repository() {
        return map(
                "id", "payments-service",
                "name", "Payments Service",
                "repositoryType", "service",
                "summary", "Runtime implementation of payments.",
                "git", map(
                        "provider", "gitlab",
                        "group", "platform",
                        "project", "payments-service",
                        "projectPath", "platform/payments-service",
                        "aliases", List.of("payments-api")
                ),
                "references", map(
                        "systems", List.of("payments"),
                        "boundedContexts", List.of("payments"),
                        "processes", List.of("checkout"),
                        "integrations", List.of("payment-provider-api")
                ),
                "matchSignals", map(
                        "strong", map(
                                "packagePrefixes", List.of("pl.example.payments"),
                                "classHints", List.of("PaymentController")
                        )
                )
        );
    }

    private Map<String, Object> codeSearchScope() {
        return map(
                "id", "payments-runtime",
                "name", "Payments runtime code scope",
                "lifecycleStatus", "active",
                "target", map(
                        "systems", List.of("payments"),
                        "processes", List.of("checkout"),
                        "boundedContexts", List.of("payments")
                ),
                "useFor", List.of("incident-analysis", "code-search"),
                "repositories", List.of(map(
                        "repoId", "payments-service",
                        "role", "primary",
                        "priority", 1,
                        "include", true,
                        "moduleIds", List.of("app"),
                        "reason", "Primary implementation."
                )),
                "packagePrefixes", List.of("pl.example.payments"),
                "classHints", List.of("PaymentController"),
                "endpointHints", List.of("/payments"),
                "databaseHints", map("schemas", List.of("PAYMENTS_APP"), "entities", List.of("PaymentEntity")),
                "limitations", List.of("Generated clients are partial.")
        );
    }

    private Map<String, Object> boundedContext() {
        return map(
                "id", "payments",
                "name", "Payments context",
                "summary", "Payment authorization and settlement context.",
                "references", map(
                        "systems", List.of("payments"),
                        "repositories", List.of("payments-service"),
                        "terms", List.of("authorization")
                ),
                "operationalSignals", map(
                        "serviceNames", List.of("payments-api"),
                        "endpointPrefixes", List.of("/payments"),
                        "packagePrefixes", List.of("pl.example.payments")
                )
        );
    }

    private OperationalContextGlossaryTerm glossaryTerm() {
        return new OperationalContextGlossaryTerm(
                "authorization",
                "Authorization",
                "payments",
                "External payment approval before settlement.",
                List.of("checkout"),
                List.of("authentication"),
                List.of("authorization", "payment approval"),
                List.of("payments"),
                List.of("authz"),
                List.of()
        );
    }

    private OperationalContextHandoffRule handoffRule() {
        return new OperationalContextHandoffRule(
                "payment-provider-timeout",
                "Payment provider timeout",
                "payments-team",
                List.of("Timeout from payment provider"),
                List.of("Local validation failure"),
                List.of("correlationId"),
                List.of("Check provider status."),
                List.of("platform-team"),
                List.of()
        );
    }

    private OperationalContextOpenQuestion openQuestion() {
        return new OperationalContextOpenQuestion(
                "open-question-payments-owner",
                "systems.yml",
                "system",
                "payments",
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
