package pl.mkn.tdw.features.configdriftviewer.input;

import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerSystemOption;

import java.util.List;

public record ConfigDriftViewerInputOptions(
        List<ConfigDriftViewerMode> modes,
        List<String> branches,
        List<RepositoryOption> repositories,
        List<ConfigDriftViewerSystemOption> systems
) {

    public ConfigDriftViewerInputOptions {
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
