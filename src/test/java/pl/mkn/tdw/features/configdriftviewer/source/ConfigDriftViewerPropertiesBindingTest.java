package pl.mkn.tdw.features.configdriftviewer.source;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import pl.mkn.tdw.integrations.gitlab.config.GitLabNamedConnectionsProperties;
import pl.mkn.tdw.integrations.http.IntegrationHttpProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDriftViewerPropertiesBindingTest {

    @Test
    void shouldBindNamedConnectionsAndRepositoryProfilesAdditively() {
        var source = new MapConfigurationPropertySource(Map.of(
                "integrations.gitlab.named.connections.config-one.base-url",
                "https://config-one.example.com",
                "integrations.gitlab.named.connections.config-one.token",
                "token-one",
                "integrations.http.ignore-ssl-errors",
                "true",
                "integrations.gitlab.named.connections.config-two.base-url",
                "https://config-two.example.com",
                "features.config-drift-viewer.repositories.runtime-config.display-name",
                "Runtime configuration",
                "features.config-drift-viewer.repositories.runtime-config.connection-id",
                "config-one",
                "features.config-drift-viewer.repositories.runtime-config.project-path",
                "CRM/runtime-config",
                "features.config-drift-viewer.branches[0]",
                "dev",
                "features.config-drift-viewer.branches[1]",
                "uat2",
                "features.config-drift-viewer.max-parallel-components",
                "7"
        ));
        var binder = new Binder(source);
        var namedConnections = new GitLabNamedConnectionsProperties();
        var httpProperties = new IntegrationHttpProperties();
        var repositories = new ConfigDriftViewerRepositoryProperties();

        binder.bind("integrations.gitlab.named", Bindable.ofInstance(namedConnections));
        binder.bind("integrations.http", Bindable.ofInstance(httpProperties));
        binder.bind(
                "features.config-drift-viewer",
                Bindable.ofInstance(repositories)
        );

        assertEquals(2, namedConnections.getConnections().size());
        assertEquals(
                "token-one",
                namedConnections.getConnections().get("config-one").getToken()
        );
        assertTrue(httpProperties.isIgnoreSslErrors());
        assertEquals(
                "config-one",
                repositories.getRepositories().get("runtime-config").getConnectionId()
        );
        assertEquals(
                "CRM/runtime-config",
                repositories.getRepositories().get("runtime-config").getProjectPath()
        );
        assertEquals(java.util.List.of("dev", "uat2"), repositories.getBranches());
        assertEquals(7, repositories.getMaxParallelComponents());
    }

    @Test
    void shouldDefaultComponentParallelismToTwenty() {
        assertEquals(20, new ConfigDriftViewerRepositoryProperties().getMaxParallelComponents());
    }
}
