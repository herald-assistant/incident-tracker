package pl.mkn.tdw.features.runtimeconfigurationverification.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeException;
import pl.mkn.tdw.integrations.gitlab.GitLabNamedConnectionRegistry;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RuntimeConfigurationRepositoryCatalog {

    private final RuntimeConfigurationRepositoryProperties properties;
    private final GitLabNamedConnectionRegistry connectionRegistry;

    public RuntimeConfigurationRepositoryProfile require(String repositoryId) {
        var normalizedId = StringUtils.hasText(repositoryId) ? repositoryId.trim() : "";
        var configured = properties.getRepositories().get(normalizedId);
        if (configured == null) {
            throw RuntimeConfigurationScopeException.repositoryNotFound(normalizedId);
        }
        if (!valid(configured)) {
            throw RuntimeConfigurationScopeException.repositoryUnavailable(normalizedId);
        }
        return profile(normalizedId, configured);
    }

    public List<RuntimeConfigurationRepositoryProfile> available() {
        return properties.getRepositories().entrySet().stream()
                .filter(entry -> StringUtils.hasText(entry.getKey()))
                .filter(entry -> valid(entry.getValue()))
                .map(entry -> profile(entry.getKey().trim(), entry.getValue()))
                .sorted((left, right) -> left.displayName().compareToIgnoreCase(right.displayName()))
                .toList();
    }

    private boolean valid(RuntimeConfigurationRepositoryProperties.Repository repository) {
        return repository != null
                && StringUtils.hasText(repository.getConnectionId())
                && StringUtils.hasText(repository.getProjectPath())
                && connectionRegistry.contains(repository.getConnectionId());
    }

    private RuntimeConfigurationRepositoryProfile profile(
            String repositoryId,
            RuntimeConfigurationRepositoryProperties.Repository repository
    ) {
        var displayName = StringUtils.hasText(repository.getDisplayName())
                ? repository.getDisplayName().trim()
                : repositoryId;
        return new RuntimeConfigurationRepositoryProfile(
                repositoryId,
                displayName,
                repository.getConnectionId().trim(),
                repository.getProjectPath().trim()
        );
    }
}
