package pl.mkn.tdw.features.configdriftviewer.input;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeResolver;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryCatalog;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryProperties;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConfigDriftViewerInputOptionsService {

    private final ConfigDriftViewerRepositoryCatalog repositoryCatalog;
    private final ConfigDriftViewerScopeResolver scopeResolver;
    private final ConfigDriftViewerRepositoryProperties repositoryProperties;

    public ConfigDriftViewerInputOptions getOptions() {
        var repositoryOptions = repositoryCatalog.available().stream()
                .map(repository -> new ConfigDriftViewerInputOptions.RepositoryOption(
                        repository.id(),
                        repository.displayName()
                ))
                .toList();
        return new ConfigDriftViewerInputOptions(
                List.of(ConfigDriftViewerMode.BASIC, ConfigDriftViewerMode.DEEP),
                List.copyOf(repositoryProperties.getBranches()),
                repositoryOptions,
                scopeResolver.availableSystems()
        );
    }
}
