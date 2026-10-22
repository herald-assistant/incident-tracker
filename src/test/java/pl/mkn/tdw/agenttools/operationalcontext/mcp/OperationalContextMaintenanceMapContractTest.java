package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextNavigation;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextOwnershipResolver;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextReadModelValidator;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextRelationIndexBuilder;
import pl.mkn.tdw.testsupport.operationalcontext.OperationalContextMaintenanceExamples.Target;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogEntityType.GLOSSARY_TERM;
import static pl.mkn.tdw.testsupport.operationalcontext.OperationalContextMaintenanceExamples.*;

class OperationalContextMaintenanceMapContractTest {
    private final OperationalContextToolMapper mapper = new OperationalContextToolMapper(
            OperationalContextNavigationTestCreator.navigation(), new OperationalContextOwnershipResolver(),
            OperationalContextNavigationTestCreator.search(), OperationalContextNavigationTestCreator.codeSearch());

    static Stream<Target> allExampleEntities() { return targets().stream(); }

    @ParameterizedTest
    @MethodSource("allExampleEntities")
    void focusedReadsRecoverEveryTargetAndOriginalPathBeyondTheDefaultLimit(Target target) {
        var raw = decoded(documents());
        for (int i = 0; i < 12; i++) entities(raw, GLOSSARY_TERM).add(new java.util.LinkedHashMap<>(Map.of(
                "id", "crm-navigation-term-" + i, "term", "CRM navigation term " + i,
                "category", "domain-term", "definition", "Fictional CRM navigation example.",
                "canonicalReferences", List.of(target.type() + ":" + target.id()))));
        var catalog = catalog(rendered(raw));
        var canonical = OperationalContextNavigationTestCreator.navigation().build(catalog).forEntity(target.type(), target.id());
        var compact = mapper.getEntity(catalog, target.type(), target.id(), null);
        var index = mapper.getEntity(catalog, target.type(), target.id(), List.of("navigation"));
        var details = mapper.getEntity(catalog, target.type(), target.id(), List.of("navigationDetails"));

        assertThat(compact.navigation()).hasSize(8);
        assertThat(compact.affordances().truncation().omittedCounts().get("navigation")).isPositive();
        assertThat(index.navigation()).hasSizeGreaterThan(8);
        assertThat(index.navigation()).hasSize(index.navigationTotal());
        assertThat(index.navigation().stream().mapToInt(item -> item.pathCount()).sum()).isEqualTo(canonical.size());
        assertThat(index.navigationPathTotal()).isEqualTo(canonical.size());
        assertThat(details.navigationDetails()).containsExactlyElementsOf(canonical);
        assertThat(index.navigation()).allSatisfy(item -> {
            assertThat(item.read().reason()).isNotBlank();
            var arguments = item.read().arguments();
            var read = mapper.getEntity(catalog, arguments.get("type").toString(), arguments.get("id").toString(), null);
            assertThat(read.id()).isEqualTo(item.id());
        });
        assertThat(details.navigationDetails()).allSatisfy(link -> {
            assertThat(link.sourceRefs()).isNotEmpty();
            assertThat(link.path()).isNotEmpty();
            assertThat(link.direction()).isIn("incoming", "outgoing", "mixed");
        });

        var key = new pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.EntityKey(
                OperationalContextNavigation.entityType(target.type()), target.id());
        var edges = new OperationalContextRelationIndexBuilder().build(catalog).relations();
        for (var edge : edges) {
            if (!edge.source().equals(key) && !edge.target().equals(key)) continue;
            var other = edge.source().equals(key) ? edge.target() : edge.source();
            if ("process-step".equals(other.type())) continue;
            assertThat(details.navigationDetails()).as("canonical edge %s", edge)
                    .anySatisfy(link -> {
                        assertThat(link.target().key()).isEqualTo(other);
                        assertThat(link.indirect()).isFalse();
                        assertThat(link.direction()).isEqualTo(edge.source().equals(key) ? "outgoing" : "incoming");
                        assertThat(link.path()).contains(edge);
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("allExampleEntities")
    void referenceCleanupKeepsFullTargetsOwnershipAndPerScopeSearchBoundaries(Target target) {
        var before = catalog(withRedundantReferences());
        var after = catalog(documents());
        var oldIndex = mapper.getEntity(before, target.type(), target.id(), List.of("navigation"));
        var newIndex = mapper.getEntity(after, target.type(), target.id(), List.of("navigation"));
        assertThat(targetKeys(newIndex)).isEqualTo(targetKeys(oldIndex));
        assertThat(mapper.getEntity(after, target.type(), target.id(), List.of("handoff")).handoff())
                .isEqualTo(mapper.getEntity(before, target.type(), target.id(), List.of("handoff")).handoff());
        var beforeCode = mapper.getEntity(before, target.type(), target.id(), List.of("codeSearch"));
        var afterCode = mapper.getEntity(after, target.type(), target.id(), List.of("codeSearch"));
        assertThat(afterCode.codeSearch()).isEqualTo(beforeCode.codeSearch());
        if (!target.id().equals("crm-contact-channel")) {
            assertThat(afterCode.codeSearch()).containsEntry("status", "available");
            assertThat(scopes(afterCode.codeSearch())).isNotEmpty();
        }
        assertSourceBoundaries(after, afterCode.codeSearch());
    }

    @Test
    void cleanupPreservesSearchMembershipForExistingIdentityAndDiscoveryQueries() {
        var raw = decoded(documents());
        var queries = new java.util.LinkedHashSet<String>();
        for (var type : pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogEntityType.values()) {
            for (var entity : entities(raw, type)) {
                queries.add(entity.get("id").toString());
                for (var field : List.of("name", "term", "title", "aliases", "useFor", "matchSignals"))
                    collectText(entity.get(field), queries);
            }
        }
        var search = OperationalContextNavigationTestCreator.search();
        var before = catalog(withRedundantReferences());
        var after = catalog(documents());
        assertThat(queries).hasSizeGreaterThan(30);
        for (var query : queries) assertThat(search.find(after, query).stream().map(match -> match.type() + ":" + match.id()).collect(Collectors.toSet()))
                .as("search membership for %s", query)
                .isEqualTo(search.find(before, query).stream().map(match -> match.type() + ":" + match.id()).collect(Collectors.toSet()));
    }

    @Test
    void contextScopeIsPreferredAndBroaderSystemBoundariesRemainSeparate() {
        var catalog = catalog(documents());
        var context = mapper.getEntity(catalog, "bounded-context", "crm-contact-preferences", List.of("codeSearch"));
        assertThat(scopes(context.codeSearch())).extracting(scope -> scope.get("id"))
                .containsExactly("crm-contact-preferences-code-scope");
        var repository = mapper.getEntity(catalog, "repository", "crm-contact-core", List.of("codeSearch"));
        var scopes = scopes(repository.codeSearch());
        assertThat(scopes).hasSize(2);
        var systemScope = scopes.stream().filter(scope -> scope.get("id").equals("crm-contact-core-code-scope")).findFirst().orElseThrow();
        var contextScope = scopes.stream().filter(scope -> scope.get("id").equals("crm-contact-preferences-code-scope")).findFirst().orElseThrow();
        assertThat(repositories(systemScope).get(0)).containsEntry("searchMode", "whole-repository").containsEntry("pathPrefixes", List.of());
        assertThat(repositories(contextScope).get(0)).containsEntry("searchMode", "path-prefixes")
                .containsEntry("pathPrefixes", List.of("src/main/java/example/crm/contact", "src/main/resources/crm/contact"));
        assertSourceBoundaries(catalog, repository.codeSearch());
    }

    @Test
    void cleanupRemovesDuplicateWarningsAndKeepsDistinctIntegrationRoles() {
        var validator = new OperationalContextReadModelValidator();
        assertThat(validator.validate(catalog(withRedundantReferences())))
                .anyMatch(finding -> "BIDIRECTIONAL_REFERENCE".equals(finding.code()))
                .anyMatch(finding -> "DUPLICATED_PARTICIPANT_REFERENCE_SYSTEM".equals(finding.code()));
        assertThat(validator.validate(catalog(documents()))).isEmpty();
        var result = mapper.getEntity(catalog(documents()), "integration", "crm-contact-to-core", List.of("navigation"));
        var roles = result.navigation().stream().flatMap(item -> item.relations().stream())
                .flatMap(relation -> relation.roles().stream()).map(role -> role.role()).collect(Collectors.toSet());
        assertThat(roles).contains("submits the contact preference", "validates and accepts the contact preference");
    }

    private static void assertSourceBoundaries(OperationalContextCatalog catalog, Map<String, Object> codeSearch) {
        for (var actualScope : scopes(codeSearch)) {
            var source = catalog.codeSearchScopes().stream().filter(scope -> scope.id().equals(actualScope.get("id"))).findFirst().orElseThrow();
            assertThat(actualScope.get("limitations")).isEqualTo(source.limitations());
            var actualRepositories = repositories(actualScope);
            assertThat(actualRepositories).hasSize(source.repositories().size());
            for (int i = 0; i < source.repositories().size(); i++) {
                var expected = source.repositories().get(i);
                assertThat(actualRepositories.get(i)).containsEntry("repoId", expected.repoId()).containsEntry("role", expected.role())
                        .containsEntry("priority", expected.priority()).containsEntry("searchMode", expected.searchMode())
                        .containsEntry("pathPrefixes", expected.pathPrefixes());
            }
        }
    }

    private static Set<String> targetKeys(OperationalContextToolDtos.OpctxEntityDetailResult detail) {
        return detail.navigation().stream().map(target -> target.type() + ":" + target.id()).collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> scopes(Map<String, Object> codeSearch) {
        return (List<Map<String, Object>>) codeSearch.get("codeSearchScopes");
    }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> repositories(Map<String, Object> scope) {
        return (List<Map<String, Object>>) scope.get("repositories");
    }
    private static void collectText(Object value, Set<String> target) {
        if (value instanceof String text) target.add(text);
        else if (value instanceof Map<?, ?> map) map.values().forEach(item -> collectText(item, target));
        else if (value instanceof List<?> list) list.forEach(item -> collectText(item, target));
    }
}
