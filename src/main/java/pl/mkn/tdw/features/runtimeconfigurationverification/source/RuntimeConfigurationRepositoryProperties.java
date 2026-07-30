package pl.mkn.tdw.features.runtimeconfigurationverification.source;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "features.runtime-configuration-verification")
public class RuntimeConfigurationRepositoryProperties {

    private Map<String, Repository> repositories = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Repository {

        private String displayName;
        private String connectionId;
        private String projectPath;
    }
}
