package pl.mkn.tdw.features.runtimeconfigurationverification.source;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "features.runtime-configuration-verification")
@Validated
public class RuntimeConfigurationRepositoryProperties {

    @NotEmpty
    private List<@Pattern(regexp = "(?:dev|test|uat|zt)\\d*") String> branches = new ArrayList<>();
    private Map<String, Repository> repositories = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Repository {

        private String displayName;
        private String connectionId;
        private String projectPath;
    }
}
