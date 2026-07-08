package pl.mkn.tdw.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.FlowExplorerProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.CODE_SEARCH_SCOPE;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.REPOSITORY;
import static pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType.SYSTEM;

class FlowExplorerRepositoryScopeServiceTest {

    @Test
    void shouldResolveRepositoriesOnlyFromSystemCodeSearchScopes() {
        var capturedQuery = new AtomicReference<OperationalContextQuery>();
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("platform/backend");
        var flowExplorerProperties = new FlowExplorerProperties();
        flowExplorerProperties.setDefaultBranch("main");
        var service = new FlowExplorerRepositoryScopeService(
                query -> {
                    capturedQuery.set(query);
                    return catalog();
                },
                gitLabProperties,
                flowExplorerProperties
        );

        var scope = service.resolve("crm-customer-profile", null);

        assertEquals(1, scope.repositoryRefCount());
        assertEquals(1, scope.repositories().size());
        assertTrue(scope.limitations().isEmpty());
        assertEquals("main", scope.resolvedRef());

        var repository = scope.repositories().get(0);
        assertEquals("crm-customer-profile-api", repository.repositoryId());
        assertEquals("crm-customer-profile-scope", repository.scopeId());
        assertEquals("primary", repository.role());
        assertEquals(1, repository.priority());
        assertEquals("Endpoint handlers for CRM profile.", repository.reason());
        assertEquals(List.of("endpoint-inventory", "flow-context"), repository.readFor());
        assertEquals("path-prefixes", repository.searchMode());
        assertEquals(List.of("src/main/java/com/example/crm/customerprofile"), repository.pathPrefixes());

        assertTrue(capturedQuery.get().includes(SYSTEM));
        assertTrue(capturedQuery.get().includes(REPOSITORY));
        assertTrue(capturedQuery.get().includes(CODE_SEARCH_SCOPE));
    }

    @Test
    void shouldNotFallbackToRepositoryReferencesDeclaredOnSystem() {
        var gitLabProperties = new GitLabProperties();
        gitLabProperties.setGroup("platform/backend");
        var service = new FlowExplorerRepositoryScopeService(
                query -> catalogWithoutCodeSearchScope(),
                gitLabProperties,
                new FlowExplorerProperties()
        );

        var scope = service.resolve("crm-customer-profile", null);

        assertEquals(0, scope.repositoryRefCount());
        assertTrue(scope.repositories().isEmpty());
        assertEquals(List.of("Operational context system has no code-search scope repositories."), scope.limitations());
    }

    private static OperationalContextDtos.OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-profile",
                        "name", "CRM Customer Profile",
                        "kind", "internal-application",
                        "references", map("repositories", List.of("legacy-system-reference-repo"))
                )),
                List.of(),
                List.of(
                        repository("legacy-system-reference-repo"),
                        repository("crm-customer-profile-api")
                ),
                List.of(map(
                        "id", "crm-customer-profile-scope",
                        "target", map("type", "system", "id", "crm-customer-profile"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-profile-api",
                                "role", "primary",
                                "priority", 1,
                                "reason", "Endpoint handlers for CRM profile.",
                                "readFor", List.of("endpoint-inventory", "flow-context"),
                                "searchMode", "path-prefixes",
                                "pathPrefixes", List.of("src/main/java/com/example/crm/customerprofile")
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static OperationalContextDtos.OperationalContextCatalog catalogWithoutCodeSearchScope() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "crm-customer-profile",
                        "name", "CRM Customer Profile",
                        "kind", "internal-application",
                        "references", map("repositories", List.of("legacy-system-reference-repo"))
                )),
                List.of(),
                List.of(repository("legacy-system-reference-repo")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> repository(String id) {
        return map(
                "id", id,
                "name", id,
                "git", map(
                        "provider", "gitlab",
                        "group", "platform/backend",
                        "projectPath", "platform/backend/" + id
                )
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
