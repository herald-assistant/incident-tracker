package pl.mkn.incidenttracker.analysis.adapter.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextRepositoryProjectPathResolverTest {

    @Test
    void shouldResolveProjectPathsThroughSystemRepositoryIds() {
        var resolver = resolver(
                List.of(system("backend", List.of("backend-repo", "shared-repo"))),
                List.of(
                        repository("backend-repo", "TENANT-ALPHA/WORKFLOWS/BACKEND", "TENANT-ALPHA", Map.of()),
                        repository("shared-repo", "TENANT-ALPHA/LIBS/SHARED", "TENANT-ALPHA", Map.of()),
                        repository("unrelated-repo", "TENANT-ALPHA/OTHER/UNRELATED", "TENANT-ALPHA", Map.of())
                )
        );

        assertEquals(
                List.of("WORKFLOWS/BACKEND", "LIBS/SHARED"),
                resolver.resolveProjectPaths("tenant-alpha", List.of("backend", "backend-7d547497bf-j44wj"))
        );
    }

    @Test
    void shouldNotResolveRepositoryOnlyRuntimeSignalWithoutMatchingSystemId() {
        var resolver = resolver(
                List.of(system("orders", List.of("orders-repo"))),
                List.of(repository(
                        "backend-repo",
                        "TENANT-ALPHA/WORKFLOWS/BACKEND",
                        "TENANT-ALPHA",
                        Map.of("containerNames", List.of("backend"))
                ))
        );

        assertEquals(List.of(), resolver.resolveProjectPaths("TENANT-ALPHA", List.of("backend")));
    }

    private static OperationalContextRepositoryProjectPathResolver resolver(
            List<Map<String, Object>> systems,
            List<Map<String, Object>> repositories
    ) {
        return new OperationalContextRepositoryProjectPathResolver(query -> {
            assertTrue(query.includes(OperationalContextEntryType.SYSTEM));
            assertTrue(query.includes(OperationalContextEntryType.REPOSITORY));
            return new OperationalContextCatalog(
                    List.of(),
                    List.of(),
                    systems,
                    List.of(),
                    repositories,
                    List.of(),
                    List.of(),
                    List.of(),
                    ""
            );
        });
    }

    private static Map<String, Object> system(String id, List<String> repositoryIds) {
        var system = new LinkedHashMap<String, Object>();
        system.put("id", id);
        system.put("repos", repositoryIds);
        return system;
    }

    private static Map<String, Object> repository(
            String id,
            String projectPath,
            String groupPath,
            Map<String, Object> runtimeMappings
    ) {
        var repository = new LinkedHashMap<String, Object>();
        repository.put("id", id);
        repository.put("gitLab", Map.of(
                "projectPath", List.of(projectPath),
                "groupPath", List.of(groupPath)
        ));
        repository.put("runtimeMappings", runtimeMappings);
        return repository;
    }
}
