package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextOwnershipResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class OperationalContextCodeSearchToolProjectionTest {
    private final OperationalContextToolMapper mapper = new OperationalContextToolMapper(
            OperationalContextNavigationTestCreator.navigation(), new OperationalContextOwnershipResolver(),
            OperationalContextNavigationTestCreator.search(), OperationalContextNavigationTestCreator.codeSearch());

    static Stream<String> entityTypes() {
        return Stream.of("system", "repository", "codeSearchScope", "process", "integration",
                "boundedContext", "team", "glossaryTerm", "handoffRule");
    }

    static Stream<String> typesWithoutScope() {
        return entityTypes().filter(type -> !"codeSearchScope".equals(type));
    }

    @ParameterizedTest
    @MethodSource("entityTypes")
    void shouldResolveTheSameScopeAndSearchBoundariesForEveryEntityType(String type) {
        var catalog = catalog(List.of(scope("crm-contacts-scope", "system", "crm-contacts",
                "path-prefixes", List.of("contacts/src", "contacts/api"))));

        var result = mapper.getEntity(catalog, type, id(type), List.of("codeSearch"));

        assertEquals(Set.of("status", "codeSearchScopes", "limitations", "validationFindings", "scopeTotal", "detail"), result.codeSearch().keySet());
        assertEquals("available", result.codeSearch().get("status"));
        var scopes = scopes(result.codeSearch());
        assertEquals(1, scopes.size());
        assertEquals("crm-contacts-scope", scopes.get(0).get("id"));
        var repository = repositories(scopes.get(0)).get(0);
        assertEquals("crm-contacts-repo", repository.get("repoId"));
        assertEquals("path-prefixes", repository.get("searchMode"));
        assertEquals(List.of("contacts/src", "contacts/api"), repository.get("pathPrefixes"));
        assertTrue(result.affordances().availableExpansions().contains("include=codeSearch"));
    }

    @ParameterizedTest
    @MethodSource("typesWithoutScope")
    void shouldExplicitlyReportAnUndefinedScopeForEveryEntityType(String type) {
        var result = mapper.getEntity(catalog(List.of()), type, id(type), List.of("codeSearch"));

        assertEquals("not-defined", result.codeSearch().get("status"));
        assertEquals(List.of(), result.codeSearch().get("codeSearchScopes"));
        assertEquals(List.of(), result.codeSearch().get("limitations"));
        assertTrue(result.codeSearch().get("validationFindings").toString().contains("NO_CODE_SEARCH_SCOPE"));
        assertFalse(result.affordances().suggestedNextReads().stream().anyMatch(read -> read.contains("include=[codeSearch]")));
    }

    @Test
    void shouldKeepDifferentSearchBoundariesOfTheSameRepositorySeparatePerScope() {
        var catalog = catalog(List.of(
                scope("crm-contacts-scope", "system", "crm-contacts", "path-prefixes", List.of("contacts/src")),
                scope("crm-contacts-view-scope", "system", "crm-contacts", "whole-repository", List.of())));

        var result = mapper.getEntity(catalog, "system", "crm-contacts", List.of("codeSearch"));
        var scopes = scopes(result.codeSearch());

        assertEquals(2, scopes.size());
        assertEquals(List.of("contacts/src"), repositories(scopes.get(0)).get(0).get("pathPrefixes"));
        assertEquals("path-prefixes", repositories(scopes.get(0)).get(0).get("searchMode"));
        assertEquals("whole-repository", repositories(scopes.get(1)).get(0).get("searchMode"));
        assertEquals(List.of(), repositories(scopes.get(1)).get(0).get("pathPrefixes"));
    }

    @Test
    void shouldRecoverAllScopesRepositoriesAndPathPrefixesWithFocusedCodeSearchInclude() {
        var prefixes = IntStream.range(0, 12).mapToObj(i -> "contacts/area-" + i).toList();
        var scopeMaps = IntStream.range(0, 12).mapToObj(i -> {
            var scope = scope("crm-contacts-scope-" + i, "system", "crm-contacts", "path-prefixes", prefixes);
            scope.put("repositories", IntStream.range(0, 12).mapToObj(j -> map(
                    "repoId", "crm-contacts-repo-" + j, "role", "supporting-library", "priority", j + 1,
                    "searchMode", "path-prefixes", "pathPrefixes", prefixes)).toList());
            return scope;
        }).toList();
        var catalog = catalog(scopeMaps);
        var compact = mapper.getEntity(catalog, "system", "crm-contacts", null);
        var expanded = mapper.getEntity(catalog, "system", "crm-contacts", List.of("codeSearch"));

        assertEquals(8, scopes(compact.codeSearch()).size());
        assertFalse(scopes(compact.codeSearch()).get(0).containsKey("repositories"));
        assertEquals("index", compact.codeSearch().get("detail"));
        assertTrue(compact.affordances().truncation().omittedCounts().get("codeSearch") > 0);
        assertEquals(12, scopes(expanded.codeSearch()).size());
        assertEquals(12, repositories(scopes(expanded.codeSearch()).get(0)).size());
        assertEquals(prefixes, repositories(scopes(expanded.codeSearch()).get(0)).get(0).get("pathPrefixes"));
        assertFalse(expanded.affordances().truncation().omittedCounts().containsKey("codeSearch"));
    }

    @Test
    void shouldNotInferAScopeFromAnUnrelatedEntityOrPopulateExcludedSections() {
        var catalog = catalog(List.of(scope("crm-isolated-scope", "system", "crm-isolated", "whole-repository", List.of())));
        var result = mapper.getEntity(catalog, "system", "crm-contacts", List.of("codeSearch"));
        assertEquals("not-defined", result.codeSearch().get("status"));
        assertEquals(List.of(), result.codeSearch().get("codeSearchScopes"));
        assertTrue(mapper.getEntity(catalog, "system", "crm-contacts", List.of("overview")).codeSearch().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("entityTypes")
    void shouldUseTypeSpecificDefaultsAndKeepFocusedCodeSearchFreeOfUnrequestedData(String type) throws Exception {
        var catalog = catalog(List.of(scope("crm-contacts-scope", "system", "crm-contacts", "path-prefixes", List.of("contacts/src"))));
        var normal = mapper.getEntity(catalog, type, id(type), null);
        var focused = mapper.getEntity(catalog, type, id(type), List.of("codeSearch"));
        assertEquals("codeSearchScope".equals(type) ? "complete" : "index", normal.codeSearch().get("detail"));
        assertEquals("complete", focused.codeSearch().get("detail"));
        assertTrue(normal.navigationDetails().isEmpty());
        assertTrue(focused.navigation().isEmpty());
        assertTrue(focused.navigationDetails().isEmpty());
        assertTrue(focused.overview().isEmpty());
        assertTrue(focused.relations().isEmpty());
        assertTrue(focused.signals().isEmpty());
        assertTrue(focused.handoff().isEmpty());
        var json = new ObjectMapper().valueToTree(focused);
        for (var section : List.of("overview", "relations", "signals", "handoff", "navigation", "navigationDetails", "navigationTotal", "navigationPathTotal", "summary", "purpose")) {
            assertFalse(json.has(section), section);
        }
        assertTrue(json.has("codeSearch"));
    }

    @Test
    void shouldExposeTheSameFocusedProfileThroughTheActualToolCallback() throws Exception {
        var catalog = catalog(List.of(scope("crm-contacts-scope", "system", "crm-contacts", "path-prefixes", List.of("contacts/src"))));
        var callbacks = MethodToolCallbackProvider.builder().toolObjects(new OperationalContextMcpTools(ignored -> catalog, mapper)).build().getToolCallbacks();
        var callback = java.util.Arrays.stream(callbacks).filter(tool -> tool.getToolDefinition().name().equals("opctx_get_entity")).findFirst().orElseThrow();
        assertTrue(callback.getToolDefinition().inputSchema().contains("navigationDetails"));
        var json = new ObjectMapper().readTree(callback.call("{\"type\":\"system\",\"id\":\"crm-contacts\",\"include\":[\"codeSearch\"]}",
                new org.springframework.ai.chat.model.ToolContext(Map.of(
                        pl.mkn.tdw.agenttools.context.AgentToolContextKeys.ANALYSIS_RUN_ID, "crm-code-search-profile"))));
        assertFalse(json.has("navigation"));
        assertFalse(json.has("overview"));
        assertEquals("contacts/src", json.at("/codeSearch/codeSearchScopes/0/repositories/0/pathPrefixes/0").asText());
    }

    @Test
    void shouldKeepEveryOriginalNavigationPathAndDistinctParticipantRolesWithoutDuplicatingReads() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(),
                List.of(map("id", "crm-publisher"), map("id", "crm-subscriber")),
                List.of(map("id", "crm-contact-events", "participants", map(
                        "source", map("system", "crm-publisher", "role", "publisher"),
                        "targets", List.of(map("system", "crm-subscriber", "role", "subscriber")),
                        "intermediaries", List.of(map("system", "crm-subscriber", "role", "relay"))))),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var original = OperationalContextNavigationTestCreator.navigation().build(catalog).forEntity("integration", "crm-contact-events");
        var index = mapper.getEntity(catalog, "integration", "crm-contact-events", List.of("navigation"));
        var details = mapper.getEntity(catalog, "integration", "crm-contact-events", List.of("navigationDetails"));
        assertEquals(original, details.navigationDetails());
        assertEquals(original.size(), index.navigation().stream().mapToInt(target -> target.pathCount()).sum());
        assertEquals(index.navigationTotal(), index.navigation().size());
        assertEquals(original.size(), index.navigationPathTotal());
        var subscriber = index.navigation().stream().filter(target -> target.id().equals("crm-subscriber")).findFirst().orElseThrow();
        assertEquals(2, subscriber.pathCount());
        assertEquals(Set.of("target-system", "intermediary-system"), subscriber.relations().stream().map(relation -> relation.relationType()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("subscriber", "relay"), subscriber.relations().stream().flatMap(relation -> relation.roles().stream()).map(role -> role.role()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(index.affordances().links().stream().noneMatch(link -> link.rel().equals("related-entity")));
        assertTrue(details.navigation().isEmpty());
        for (var target : index.navigation()) {
            var next = mapper.getEntity(catalog, target.read().arguments().get("type").toString(), target.read().arguments().get("id").toString(), null);
            assertEquals(target.id(), next.id());
        }
    }

    @Test
    void shouldPreferThePrimaryProcessSystemAndRecoverCompleteSectionsOutsideDefaults() {
        var catalog = catalog(List.of(scope("crm-contacts-scope", "system", "crm-contacts", "path-prefixes", List.of("contacts/src"))));
        var process = mapper.getEntity(catalog, "process", "crm-contact-update", null);
        assertEquals("crm-contacts", process.navigation().get(0).id());
        assertTrue(process.overview().get("processBoundary").toString().contains("CRM contact update is recorded"));
        assertTrue(process.relations().get("steps").toString().contains("Validate the CRM contact"));
        assertFalse(process.overview().get("lifecycle").toString().contains("triggers"));
        assertTrue(mapper.getEntity(catalog, "process", "crm-contact-update", List.of("overview")).overview().get("lifecycle").toString().contains("triggers"));
        var context = mapper.getEntity(catalog, "boundedContext", "crm-contact-profile", null);
        assertTrue(context.overview().get("semanticBoundary").toString().contains("Contact identity remains stable"));
        var scope = mapper.getEntity(catalog, "codeSearchScope", "crm-contacts-scope", null);
        assertTrue(scope.relations().isEmpty());
        assertTrue(scope.handoff().isEmpty());
    }

    @Test
    void shouldKeepAllSearchBoundariesInTheDefaultCodeScopeEvenBeyondTheListLimit() {
        var prefixes = IntStream.range(0, 15).mapToObj(i -> "contacts/area-" + i).toList();
        var catalog = catalog(List.of(scope("crm-contacts-scope", "system", "crm-contacts", "path-prefixes", prefixes)));
        var result = mapper.getEntity(catalog, "codeSearchScope", "crm-contacts-scope", null);
        assertEquals(prefixes, repositories(scopes(result.codeSearch()).get(0)).get(0).get("pathPrefixes"));
        assertFalse(result.affordances().truncation().omittedCounts().containsKey("codeSearch"));
        assertThrows(IllegalArgumentException.class, () -> mapper.getEntity(catalog, "system", "crm-contacts", List.of("invalid-section")));
    }

    private static String id(String type) {
        return switch (type) {
            case "repository" -> "crm-contacts-repo";
            case "codeSearchScope" -> "crm-contacts-scope";
            case "process" -> "crm-contact-update";
            case "integration" -> "crm-contact-sync";
            case "boundedContext" -> "crm-contact-profile";
            case "team" -> "crm-contact-team";
            case "glossaryTerm" -> "crm-contact";
            case "handoffRule" -> "crm-contact-handoff";
            default -> "crm-contacts";
        };
    }

    private static OperationalContextCatalog catalog(List<Map<String, Object>> scopes) {
        var references = map("systems", List.of("crm-contacts"));
        var repositories = Stream.concat(Stream.of("crm-contacts-repo"), scopes.stream()
                .flatMap(scope -> repositories(scope).stream()).map(repository -> repository.get("repoId").toString()))
                .distinct().map(id -> map("id", id, "git", map("group", "CRM", "project", id, "projectPath", "CRM/" + id)))
                .toList();
        return OperationalContextDtos.catalogFromRaw(
                List.of(map("id", "crm-contact-team")),
                List.of(map("id", "crm-contact-update", "participants", map("primarySystems", List.of("crm-contacts")),
                        "processBoundary", map("endsWhen", List.of("CRM contact update is recorded")),
                        "lifecycle", map("statuses", List.of("requested", "recorded"), "triggers", List.of(map("type", "business-action", "name", "CRM contact update"))),
                        "steps", List.of(map("id", "crm-validate-contact", "name", "Validate the CRM contact", "summary", "Check the CRM contact profile")))),
                List.of(map("id", "crm-contacts", "ownership", map("ownerTeamIds", List.of("crm-contact-team"), "ownershipStatus", "explicit")), map("id", "crm-isolated")),
                List.of(map("id", "crm-contact-sync", "participants", map("source", map("system", "crm-contacts")))),
                repositories,
                scopes,
                List.of(map("id", "crm-contact-profile", "references", references, "semanticBoundary", map("invariants", List.of("Contact identity remains stable")))),
                List.of(OperationalContextDtos.glossaryTerm(map("id", "crm-contact", "term", "CRM Contact",
                        "category", "crm-domain", "definition", "A fictional CRM contact profile.",
                        "canonicalReferences", List.of("system:crm-contacts")))),
                List.of(OperationalContextDtos.handoffRule(map("id", "crm-contact-handoff", "references", references))),
                List.of());
    }

    private static Map<String, Object> scope(String id, String type, String target, String searchMode, List<String> pathPrefixes) {
        return map("id", id, "target", map("type", type, "id", target), "repositories", List.of(map(
                "repoId", "crm-contacts-repo", "role", "primary", "priority", 1,
                "searchMode", searchMode, "pathPrefixes", pathPrefixes)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> scopes(Map<String, Object> codeSearch) {
        return (List<Map<String, Object>>) codeSearch.get("codeSearchScopes");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> repositories(Map<String, Object> scope) {
        return (List<Map<String, Object>>) scope.get("repositories");
    }

    private static Map<String, Object> map(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
