package pl.mkn.tdw.integrations.gitlab;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabNamedConnectionRegistryTest {

    @Test
    void shouldKeepTokenOutOfConnectionDescriptionAndErrors() {
        var properties = new GitLabNamedConnectionsProperties();
        var configured = new GitLabNamedConnectionsProperties.Connection();
        configured.setBaseUrl("https://config.example.com");
        configured.setToken("never-show-this-token");
        properties.setConnections(Map.of("config", configured));
        var registry = new GitLabNamedConnectionRegistry(properties);

        assertFalse(registry.require("config").toString().contains("never-show-this-token"));

        var exception = assertThrows(
                GitLabExactReadException.class,
                () -> registry.require("missing")
        );
        assertFalse(exception.getMessage().contains("never-show-this-token"));
    }
}
