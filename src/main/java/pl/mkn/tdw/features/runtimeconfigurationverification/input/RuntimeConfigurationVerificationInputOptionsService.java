package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryCatalog;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryProperties;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationInputOptionsService {

    private final RuntimeConfigurationRepositoryCatalog repositoryCatalog;
    private final RuntimeConfigurationScopeResolver scopeResolver;
    private final RuntimeConfigurationRepositoryProperties repositoryProperties;

    public RuntimeConfigurationVerificationInputOptions getOptions() {
        var repositoryOptions = repositoryCatalog.available().stream()
                .map(repository -> new RuntimeConfigurationVerificationInputOptions.RepositoryOption(
                        repository.id(),
                        repository.displayName()
                ))
                .toList();
        return new RuntimeConfigurationVerificationInputOptions(
                List.of(RuntimeConfigurationVerificationMode.BASIC, RuntimeConfigurationVerificationMode.DEEP),
                List.copyOf(repositoryProperties.getBranches()),
                repositoryOptions,
                scopeResolver.availableSystems()
        );
    }
}
