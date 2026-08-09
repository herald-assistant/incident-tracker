package pl.mkn.tdw.features.configdriftviewer.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeException;
import pl.mkn.tdw.integrations.gitlab.GitLabNamedConnectionRegistry;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ConfigDriftViewerRepositoryCatalog {

    private final ConfigDriftViewerRepositoryProperties properties;
    private final GitLabNamedConnectionRegistry connectionRegistry;

    public ConfigDriftViewerRepositoryProfile require(String repositoryId) {
        var normalizedId = StringUtils.hasText(repositoryId) ? repositoryId.trim() : "";
        var configured = properties.getRepositories().get(normalizedId);
        if (configured == null) {
            throw ConfigDriftViewerScopeException.repositoryNotFound(normalizedId);
        }
        if (!valid(configured)) {
            throw ConfigDriftViewerScopeException.repositoryUnavailable(normalizedId);
        }
        return profile(normalizedId, configured);
    }

    public List<ConfigDriftViewerRepositoryProfile> available() {
        return properties.getRepositories().entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()))
                .filter(entry -> valid(entry.getValue()))
                .map(entry -> profile(entry.getKey().trim(), entry.getValue()))
                .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                .toList();
    }

    private boolean valid(ConfigDriftViewerRepositoryProperties.Repository repository) {
        return repository != null
                && StringUtils.hasText(repository.getConnectionId())
                && StringUtils.hasText(repository.getProjectPath())
                && connectionRegistry.contains(repository.getConnectionId());
    }

    private ConfigDriftViewerRepositoryProfile profile(
            String repositoryId,
            ConfigDriftViewerRepositoryProperties.Repository repository
    ) {
        var displayName = StringUtils.hasText(repository.getDisplayName())
                ? repository.getDisplayName().trim()
                : repositoryId;
        return new ConfigDriftViewerRepositoryProfile(
                repositoryId,
                displayName,
                repository.getConnectionId().trim(),
                repository.getProjectPath().trim()
        );
    }
}
