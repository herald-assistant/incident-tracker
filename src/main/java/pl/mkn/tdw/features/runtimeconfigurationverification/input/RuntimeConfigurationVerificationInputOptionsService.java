package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryCatalog;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationInputOptionsService {

    private final RuntimeConfigurationRepositoryCatalog repositoryCatalog;
    private final RuntimeConfigurationScopeResolver scopeResolver;

    public RuntimeConfigurationVerificationInputOptions getOptions() {
        var repositoryOptions = repositoryCatalog.available().stream()
                .map(repository -> new RuntimeConfigurationVerificationInputOptions.RepositoryOption(
                        repository.id(),
                        repository.displayName()
                ))
                .toList();
        return new RuntimeConfigurationVerificationInputOptions(
                List.of(RuntimeConfigurationVerificationMode.BASIC, RuntimeConfigurationVerificationMode.DEEP),
                branches(),
                repositoryOptions,
                scopeResolver.availableSystems()
        );
    }

    private List<String> branches() {
        return IntStream.rangeClosed(0, 9)
                .boxed()
                .flatMap(index -> java.util.stream.Stream.of("dev" + index, "zt00" + index))
                .toList();
    }
}
