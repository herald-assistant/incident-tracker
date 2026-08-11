package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextAdapterTestCreator;
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
        assertEquals("crm-customer-profile", firstPage.items().get(0).id());

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
        assertTrue(result.overview().get("runtime").toString().contains("crm/notifications"));
        assertTrue(result.relations().containsKey("references"));
        assertEquals("CRM managed platform provider", result.relations().get("externalOwner"));
        assertTrue(result.signals().containsKey("matchSignals"));
        assertEquals(1, result.signals().size());
        assertTrue(result.codeSearch().containsKey("codeSearchScopes"));
        assertFalse(result.codeSearch().containsKey("localCodeSearchScope"));
        assertTrue(result.handoff().containsKey("resolvedOwnership"));
        assertTrue(result.handoff().get("resolvedOwnership").toString().contains("notifications-team"));
        assertEquals(1, result.openQuestions().size());
        assertTrue(result.sourceRefs().contains("systems.yml#notifications"));
        assertFalse(result.overview().containsKey("payload"));
        assertFalse(result.relations().containsKey("rawSourcePreview"));
        assertFalse(result.toString().contains("rawSourcePreview"));
        assertFalse(result.toString().contains("futureCrmRuntimeHint"));
    }

    @Test
    void shouldExposeAnonymizedCrmRepositoryEvidenceAndExplorationGuidanceWithoutRawExtensions() {
        var result = tools.getEntity(
                "repository",
                "notifications-service",
                List.of("overview", "codeSearch"),
                "Sprawdzam zanonimizowane wskazowki repozytorium CRM.",
                null
        );

        assertTrue(result.overview().get("evidence").toString().contains("crm/notifications/pom.xml"));
        assertTrue(result.overview().get("evidence").toString().contains("build-definition"));
        assertTrue(result.codeSearch().get("llmToolHints").toString().contains("CRM contact notification"));
        assertTrue(result.codeSearch().get("llmToolHints").toString().contains("CRM authentication account service"));
        assertFalse(result.toString().contains("futureCrmEvidenceHint"));
        assertFalse(result.toString().contains("futureCrmToolHint"));
    }

    @Test
    void shouldExposeAnonymizedCrmBoundedContextSemanticsWithoutRawExtensions() {
        var result = tools.getEntity(
                "bounded-context",
                "notifications",
                List.of("overview", "signals"),
                "Ground an anonymized CRM notification question",
                null
        );

        assertTrue(result.overview().get("localLanguageSummary").toString().contains("CRM notification"));
        assertTrue(result.overview().get("scope").toString().contains("CRM contact notification delivery"));
        assertTrue(result.overview().get("semanticBoundary").toString().contains("CRM notification acknowledgement"));
        assertTrue(result.overview().get("evidence").toString().contains("Anonymized CRM notification glossary"));
        assertTrue(result.signals().get("llmToolHints").toString().contains("CRM contact notification"));
        assertTrue(result.signals().get("llmToolHints").toString().contains("NotificationTemplate"));
        assertFalse(result.toString().contains("futureCrmScopeHint"));
        assertFalse(result.toString().contains("futureCrmSemanticHint"));
        assertFalse(result.toString().contains("futureCrmEvidenceHint"));
        assertFalse(result.toString().contains("futureCrmToolHint"));
    }

    @Test
    void shouldExposeStructuredAnonymousCrmFailureArtifactsAndCoverageWithoutRawPayload() {
        var process = tools.getEntity(
                "process",
                "customer-notification",
                List.of("overview", "signals"),
                "Sprawdzam zanonimizowany proces CRM.",
                null
        );
        assertTrue(process.overview().get("dataAndArtifacts").toString().contains("Anonymized CRM contact change request"));
        assertTrue(process.overview().get("processBoundary").toString().contains("CRM Contact Preference Management"));
        assertTrue(process.overview().get("lifecycle").toString().contains("CRM contact validation succeeds"));
        assertTrue(process.overview().get("completionSignals").toString().contains("CRM contact confirmation is recorded"));
        assertTrue(process.signals().get("failureModes").toString().contains("crm-notification-timeout"));
        assertFalse(process.toString().contains("futureCrmRawHint"));

        var system = tools.getEntity(
                "system",
                "notifications",
                List.of("sourceCoverage"),
                "Sprawdzam zanonimizowane pokrycie zrodel CRM.",
                null
        );
        assertEquals("partial", system.sourceCoverage().get("status"));
        assertTrue(system.sourceCoverage().get("limitations").toString().contains("CRM provider internals were not reviewed"));
        assertTrue(system.affordances().limitations().stream().anyMatch(limit -> limit.contains("CRM provider internals")));
        assertFalse(system.sourceCoverage().containsKey("futureCrmRawHint"));
    }

    @Test
    void shouldKeepDefaultEntityToolPayloadCompact() {
        var result = tools.getEntity(
                "repository",
                "notifications-service",
                null,
                "Pobieram domyslny kompaktowy opis repozytorium.",
                null
        );

        assertEquals("default", result.affordances().profile());
        if (result.affordances().truncation().truncated()) {
            assertTrue(result.affordances().omittedBecause().stream()
                    .anyMatch(reason -> reason.contains("compacted")));
        }
        assertTrue(result.affordances().truncation().returnedCounts().containsKey("overviewValues"));
        assertTrue(result.affordances().suggestedTools().contains("opctx_search"));
        assertFalse(result.toString().contains("rawSourcePreview"));
        assertFalse(result.toString().contains("payload"));
    }

    @Test
    void shouldKeepEntityPayloadCompactForLargeCodeSearchScope() throws Exception {
        var catalogTools = new OperationalContextMcpTools(
                OperationalContextAdapterTestCreator.create(new OperationalContextProperties()),
                new OperationalContextToolMapper()
        );

        var result = catalogTools.getEntity(
                "codeSearchScope",
                "crm-customer-service-code-search",
                null,
                "Sprawdzam kompaktowy payload narzedzia dla duzego scope.",
                null
        );
        var json = objectMapper.writeValueAsString(result);

        assertEquals("default", result.affordances().profile());
        assertTrue(result.affordances().truncation().returnedCounts().containsKey("codeSearchValues"));
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
        assertTrue(boundedContext.signals().containsKey("matchSignals"));

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
                "notifications-code-scope",
                List.of("relations", "codeSearch", "sourceCoverage"),
                "Sprawdzam zakres szukania kodu.",
                null
        );
        assertEquals("Notifications code scope", codeSearchScope.label());
        assertTrue(codeSearchScope.relations().containsKey("repositories"));
        assertTrue(codeSearchScope.codeSearch().containsKey("repositories"));
        assertTrue(codeSearchScope.codeSearch().containsKey("limitations"));
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
                List.of(system("crm-customer-profile", "CRM Customer Profile", "crm-customer-profile-api"), system("notifications", "Notifications", "notifications-api")),
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
                "id", "customer-notification",
                "name", "Customer Notification",
                "summary", "CRM customer notification process.",
                "participants", map(
                        "primarySystems", List.of("notifications"),
                        "externalSystems", List.of("notification-gateway")
                ),
                "references", map(
                        "systems", List.of("notifications"),
                        "boundedContexts", List.of("notifications")
                ),
                "processBoundary", map(
                        "businessCapability", "CRM Contact Preference Management",
                        "startsWhen", List.of("An anonymized CRM contact update is accepted."),
                        "endsWhen", List.of("The CRM contact view confirms the update."),
                        "includes", List.of("CRM contact preference validation"),
                        "excludes", List.of("Authentication credential lifecycle"),
                        "assumptions", List.of("CRM contact identity is already resolved."),
                        "futureCrmRawHint", "must-not-leak"
                ),
                "lifecycle", map(
                        "triggers", List.of(map(
                                "type", "api",
                                "name", "CRM contact update",
                                "futureCrmRawHint", "must-not-leak"
                        )),
                        "statuses", List.of("requested", "applied"),
                        "transitions", List.of(map(
                                "from", "requested",
                                "to", "applied",
                                "trigger", "CRM contact validation succeeds.",
                                "futureCrmRawHint", "must-not-leak"
                        )),
                        "terminalStates", List.of("applied"),
                        "successOutcomes", List.of("CRM contact preference is applied."),
                        "futureCrmRawHint", "must-not-leak"
                ),
                "completionSignals", map(
                        "successful", List.of("CRM contact confirmation is recorded."),
                        "partial", List.of("CRM projection remains pending."),
                        "failed", List.of("CRM validation rejection is recorded."),
                        "cancelled", List.of("CRM cancellation is recorded."),
                        "futureCrmRawHint", "must-not-leak"
                ),
                "failureModes", List.of(map(
                        "id", "crm-notification-timeout",
                        "name", "CRM notification timeout",
                        "summary", "An anonymized CRM notification is not confirmed in time.",
                        "affectedStep", "publish-crm-notification",
                        "signals", List.of("CRM confirmation missing"),
                        "futureCrmRawHint", "must-not-leak"
                )),
                "dataAndArtifacts", map(
                        "inputArtifacts", List.of("Anonymized CRM contact change request"),
                        "outputArtifacts", List.of("CRM contact notification confirmation"),
                        "futureCrmRawHint", "must-not-leak"
                )
        );
    }

    private Map<String, Object> system(String id, String name, String alias) {
        return map(
                "id", id,
                "name", name,
                "criticality", id.equals("notifications") ? "high" : "medium",
                "summary", name + " system.",
                "participants", id.equals("notifications")
                        ? map("externalOwner", "CRM managed platform provider", "futureCrmParticipantHint", "must-not-leak")
                        : map(),
                "runtime", id.equals("notifications")
                        ? map("configurationDirectory", "crm/notifications", "futureCrmRuntimeHint", "must-not-leak")
                        : map(),
                "aliases", List.of(alias),
                "references", map(
                        "repositories", id.equals("notifications") ? List.of("notifications-service") : List.of(),
                        "processes", id.equals("notifications") ? List.of("customer-notification") : List.of(),
                        "boundedContexts", id.equals("notifications") ? List.of("notifications") : List.of(),
                        "teams", id.equals("notifications") ? List.of("notifications-team") : List.of()
                ),
                "ownership", id.equals("notifications")
                        ? ownership(List.of("notifications-team"), null, "high")
                        : map(),
                "matchSignals", map(
                        "strong", map(
                                "markers", List.of(alias)
                        )
                ),
                "sourceCoverage", map(
                        "status", "partial",
                        "scannedSources", List.of("Anonymized CRM service notes"),
                        "expectedSources", List.of("CRM provider contract"),
                        "limitations", List.of("CRM provider internals were not reviewed"),
                        "futureCrmRawHint", "must-not-leak"
                )
        );
    }

    private Map<String, Object> integration() {
        return map(
                "id", "notification-gateway-api",
                "name", "Notification Gateway API",
                "summary", "External notification delivery gateway.",
                "category", "external-service-call",
                "integrationStyle", "synchronous-request",
                "flowDirection", "outbound",
                "participants", map(
                        "source", map("system", "notifications"),
                        "targets", List.of(map("system", "notification-gateway", "externalOwner", "Provider")),
                        "finalTargets", List.of(map("system", "notification-gateway", "role", "server"))
                )
        );
    }

    private Map<String, Object> repository() {
        return map(
                "id", "notifications-service",
                "name", "Notifications Service",
                "repositoryType", "service",
                "summary", "Repository for notifications capability.",
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
                        "processes", List.of("customer-notification"),
                        "integrations", List.of("notification-gateway-api")
                ),
                "matchSignals", map(
                        "strong", map(
                                "projectNames", List.of("notifications-service"),
                                "domainTerms", List.of("notification delivery")
                        )
                ),
                "evidence", List.of(map(
                        "sourceRef", "crm/notifications/pom.xml",
                        "evidenceType", "build-definition",
                        "note", "Anonymized CRM notification module.",
                        "futureCrmEvidenceHint", "must-not-leak"
                )),
                "llmToolHints", map(
                        "answerWhenUserMentions", List.of("CRM contact notification"),
                        "disambiguateFrom", List.of("CRM authentication account service"),
                        "futureCrmToolHint", "must-not-leak"
                )
        );
    }

    private Map<String, Object> codeSearchScope() {
        return map(
                "id", "notifications-code-scope",
                "name", "Notifications code scope",
                "scopeType", "system",
                "lifecycleStatus", "active",
                "target", map("type", "system", "id", "notifications"),
                "useFor", List.of("incident-analysis", "code-search"),
                "repositories", List.of(map(
                        "repoId", "notifications-service",
                        "role", "primary",
                        "priority", 1,
                        "searchMode", "whole-repository",
                        "reason", "Primary repository."
                )),
                "limitations", List.of("Generated clients are partial.")
        );
    }

    private Map<String, Object> boundedContext() {
        return map(
                "id", "notifications",
                "name", "Notifications context",
                "summary", "Notification delivery context.",
                "type", "supporting-domain",
                "localLanguageSummary", List.of("CRM notification means a contact message, not an authentication challenge."),
                "scope", map(
                        "includes", List.of("CRM contact notification delivery"),
                        "excludes", List.of("Authentication challenge delivery"),
                        "businessCapabilities", List.of("CRM Contact Communication"),
                        "coreEntities", List.of("NotificationTemplate"),
                        "keyDecisions", List.of("Whether a CRM contact notification is ready"),
                        "futureCrmScopeHint", "must-not-leak"
                ),
                "semanticBoundary", map(
                        "coreConcepts", List.of("CRM contact notification"),
                        "invariants", List.of("CRM notification acknowledgement is recorded once"),
                        "ownsLanguage", List.of("contact notification"),
                        "doesNotOwn", List.of("authentication challenge"),
                        "futureCrmSemanticHint", "must-not-leak"
                ),
                "evidence", List.of(map(
                        "sourceRef", "Anonymized CRM notification glossary",
                        "evidenceType", "domain-documentation",
                        "futureCrmEvidenceHint", "must-not-leak"
                )),
                "llmToolHints", map(
                        "answerWhenUserMentions", List.of("CRM contact notification"),
                        "disambiguateFrom", List.of("CRM authentication challenge"),
                        "usefulSearchKeywords", List.of("NotificationTemplate"),
                        "explanationStyle", "Explain as the CRM notification boundary.",
                        "futureCrmToolHint", "must-not-leak"
                ),
                "references", map(
                        "systems", List.of("notifications"),
                        "repositories", List.of("notifications-service"),
                        "terms", List.of("authorization")
                ),
                "ownership", ownership(List.of("notifications-team"), null, "high"),
                "matchSignals", map(
                        "strong", map(
                                "domainTerms", List.of("notification delivery")
                        )
                )
        );
    }

    private OperationalContextGlossaryTerm glossaryTerm() {
        return new OperationalContextGlossaryTerm(
                "authorization",
                "Authorization",
                "notifications",
                "External notification delivery before acknowledgement.",
                List.of("customer-notification"),
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
                List.of("Timeout from notification gateway"),
                List.of("Local validation failure"),
                List.of("correlationId"),
                List.of("Check provider status."),
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

    private Map<String, Object> ownership(List<String> ownerTeamIds, String ownerLabel, String confidence) {
        return map(
                "ownerTeamIds", ownerTeamIds,
                "ownerLabel", ownerLabel,
                "ownershipStatus", "explicit",
                "confidence", confidence,
                "source", "test"
        );
    }
}
