package pl.mkn.tdw.features.runtimeconfigurationverification.input;

import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationSystemOption;

import java.util.List;

public record RuntimeConfigurationVerificationInputOptions(
        List<RuntimeConfigurationVerificationMode> modes,
        List<String> branches,
        List<RepositoryOption> repositories,
        List<RuntimeConfigurationSystemOption> systems
) {

    public RuntimeConfigurationVerificationInputOptions {
        modes = List.copyOf(modes);
        branches = List.copyOf(branches);
        repositories = List.copyOf(repositories);
        systems = List.copyOf(systems);
    }

    public record RepositoryOption(
            String id,
            String label
    ) {
    }
}
