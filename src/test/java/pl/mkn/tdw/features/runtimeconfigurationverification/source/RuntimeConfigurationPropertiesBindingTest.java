package pl.mkn.tdw.features.runtimeconfigurationverification.source;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import pl.mkn.tdw.integrations.gitlab.GitLabNamedConnectionsProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationPropertiesBindingTest {

    @Test
    void shouldBindNamedConnectionsAndRepositoryProfilesAdditively() {
        var source = new MapConfigurationPropertySource(Map.of(
                "integrations.gitlab.named.connections.config-one.base-url",
                "https://config-one.example.com",
                "integrations.gitlab.named.connections.config-one.token",
                "token-one",
                "integrations.gitlab.named.connections.config-one.ignore-ssl-errors",
                "true",
                "integrations.gitlab.named.connections.config-two.base-url",
                "https://config-two.example.com",
                "features.runtime-configuration-verification.repositories.runtime-config.display-name",
                "Runtime configuration",
                "features.runtime-configuration-verification.repositories.runtime-config.connection-id",
                "config-one",
                "features.runtime-configuration-verification.repositories.runtime-config.project-path",
                "platform/runtime-config",
                "features.runtime-configuration-verification.branches[0]",
                "dev",
                "features.runtime-configuration-verification.branches[1]",
                "uat2"
        ));
        var binder = new Binder(source);
        var namedConnections = new GitLabNamedConnectionsProperties();
        var repositories = new RuntimeConfigurationRepositoryProperties();

        binder.bind("integrations.gitlab.named", Bindable.ofInstance(namedConnections));
        binder.bind(
                "features.runtime-configuration-verification",
                Bindable.ofInstance(repositories)
        );

        assertEquals(2, namedConnections.getConnections().size());
        assertEquals(
                "token-one",
                namedConnections.getConnections().get("config-one").getToken()
        );
        assertTrue(namedConnections.getConnections().get("config-one").isIgnoreSslErrors());
        assertEquals(
                "config-one",
                repositories.getRepositories().get("runtime-config").getConnectionId()
        );
        assertEquals(
                "platform/runtime-config",
                repositories.getRepositories().get("runtime-config").getProjectPath()
        );
        assertEquals(java.util.List.of("dev", "uat2"), repositories.getBranches());
    }
}
