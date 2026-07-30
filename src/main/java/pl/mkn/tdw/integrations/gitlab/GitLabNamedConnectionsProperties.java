package pl.mkn.tdw.integrations.gitlab;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "integrations.gitlab.named")
public class GitLabNamedConnectionsProperties {

    private Map<String, Connection> connections = new LinkedHashMap<>();
    private int maxFileCharacters = 1_000_000;

    @Getter
    @Setter
    public static class Connection {

        private String baseUrl;
        private String token;
        private boolean ignoreSslErrors;
    }
}
