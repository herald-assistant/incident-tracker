package pl.mkn.tdw.features.runtimeconfigurationverification.source;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeException;
import pl.mkn.tdw.integrations.gitlab.GitLabNamedConnectionRegistry;
import pl.mkn.tdw.integrations.gitlab.GitLabNamedConnectionsProperties;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeConfigurationRepositoryCatalogTest {

    @Test
    void shouldResolveOnlyProfilesBackedByValidNamedConnections() {
        var namedProperties = new GitLabNamedConnectionsProperties();
        var connection = new GitLabNamedConnectionsProperties.Connection();
        connection.setBaseUrl("https://config.example.com");
        connection.setToken("backend-only-token");
        namedProperties.setConnections(Map.of("config-gitlab", connection));

        var repositoryProperties = new RuntimeConfigurationRepositoryProperties();
        var repositories = new LinkedHashMap<String, RuntimeConfigurationRepositoryProperties.Repository>();
        repositories.put(
                "runtime-config",
                repository("Runtime configuration", "config-gitlab", "platform/runtime-config")
        );
        repositories.put(
                "broken",
                repository("Broken", "missing-connection", "platform/broken")
        );
        repositoryProperties.setRepositories(repositories);
        var catalog = new RuntimeConfigurationRepositoryCatalog(
                repositoryProperties,
                new GitLabNamedConnectionRegistry(namedProperties)
        );

        var available = catalog.available();

        assertEquals(1, available.size());
        assertEquals("runtime-config", available.get(0).id());
        assertEquals("config-gitlab", catalog.require("runtime-config").connectionId());
        assertEquals(
                "RUNTIME_CONFIGURATION_REPOSITORY_UNAVAILABLE",
                assertThrows(
                        RuntimeConfigurationScopeException.class,
                        () -> catalog.require("broken")
                ).code()
        );
    }

    private static RuntimeConfigurationRepositoryProperties.Repository repository(
            String displayName,
            String connectionId,
            String projectPath
    ) {
        var repository = new RuntimeConfigurationRepositoryProperties.Repository();
        repository.setDisplayName(displayName);
        repository.setConnectionId(connectionId);
        repository.setProjectPath(projectPath);
        return repository;
    }
}
