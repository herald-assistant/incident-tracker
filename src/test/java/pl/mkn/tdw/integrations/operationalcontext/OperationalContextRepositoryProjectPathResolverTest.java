package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextRepositoryProjectPathResolverTest {

    @Test
    void shouldResolveProjectPathsThroughSystemCodeSearchScope() {
        var resolver = resolver(
                List.of(system("crm-customer-service")),
                List.of(
                        repository("crm-customer-repo", "CRM/SERVICES/CRM_CUSTOMER", "CRM", Map.of()),
                        repository("crm-shared-repo", "CRM/LIBS/CRM_SHARED", "CRM", Map.of()),
                        repository("crm-unrelated-repo", "CRM/OTHER/CRM_UNUSED", "CRM", Map.of())
                ),
                List.of(scope(
                        "crm-customer-search",
                        "system",
                        "crm-customer-service",
                        List.of("crm-customer-repo", "crm-shared-repo")
                ))
        );

        assertEquals(
                List.of("SERVICES/CRM_CUSTOMER", "LIBS/CRM_SHARED"),
                resolver.resolveProjectPaths(
                        "crm",
                        List.of("crm-customer-service", "crm-customer-service-7d547497bf-j44wj")
                )
        );
    }

    @Test
    void shouldNotResolveRepositoryOnlySignalWithoutMatchingSystemId() {
        var resolver = resolver(
                List.of(system("crm-customer-service")),
                List.of(repository(
                        "crm-customer-repo",
                        "CRM/SERVICES/CRM_CUSTOMER",
                        "CRM",
                        Map.of("markers", List.of("crm-customer"))
                )),
                List.of(scope(
                        "crm-customer-search",
                        "system",
                        "crm-customer-service",
                        List.of("crm-customer-repo")
                ))
        );

        assertEquals(List.of(), resolver.resolveProjectPaths("CRM", List.of("crm-customer")));
    }

    @Test
    void shouldIgnoreScopeThatTargetsBoundedContextForSystemResolution() {
        var resolver = resolver(
                List.of(system("crm-customer-service")),
                List.of(repository(
                        "crm-customer-repo",
                        "CRM/SERVICES/CRM_CUSTOMER",
                        "CRM",
                        Map.of()
                )),
                List.of(scope(
                        "crm-customer-context-search",
                        "bounded-context",
                        "crm-customer-profile",
                        List.of("crm-customer-repo")
                ))
        );

        assertEquals(List.of(), resolver.resolveProjectPaths("CRM", List.of("crm-customer-service")));
    }

    private static OperationalContextRepositoryProjectPathResolver resolver(
            List<Map<String, Object>> systems,
            List<Map<String, Object>> repositories,
            List<Map<String, Object>> codeSearchScopes
    ) {
        return new OperationalContextRepositoryProjectPathResolver(query -> {
            assertTrue(query.includes(OperationalContextEntryType.SYSTEM));
            assertTrue(query.includes(OperationalContextEntryType.REPOSITORY));
            assertTrue(query.includes(OperationalContextEntryType.CODE_SEARCH_SCOPE));
            return OperationalContextDtos.catalogFromRaw(
                    List.of(),
                    List.of(),
                    systems,
                    List.of(),
                    repositories,
                    codeSearchScopes,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ""
            );
        });
    }

    private static Map<String, Object> system(String id) {
        var system = new LinkedHashMap<String, Object>();
        system.put("id", id);
        return system;
    }

    private static Map<String, Object> scope(
            String id,
            String targetType,
            String targetId,
            List<String> repositoryIds
    ) {
        return Map.of(
                "id", id,
                "target", Map.of("type", targetType, "id", targetId),
                "repositories", repositoryIds.stream()
                        .map(repositoryId -> Map.<String, Object>of("repoId", repositoryId))
                        .toList()
        );
    }

    private static Map<String, Object> repository(
            String id,
            String projectPath,
            String groupPath,
            Map<String, Object> matchSignals
    ) {
        var repository = new LinkedHashMap<String, Object>();
        repository.put("id", id);
        repository.put("git", Map.of(
                "projectPath", List.of(projectPath),
                "group", List.of(groupPath)
        ));
        repository.put("matchSignals", Map.of("strong", matchSignals));
        return repository;
    }
}
