package pl.mkn.tdw.features.runtimeconfigurationverification.deep.model;

import java.util.List;

public record RuntimeConfigurationDeepPreflight(
        RuntimeConfigurationDeepPreflightStatus status,
        String repositoryId,
        String systemId,
        String systemLabel,
        String resolvedConfigurationDirectory,
        List<RuntimeConfigurationDeepRepositoryScope> repositories,
        List<RuntimeConfigurationDeepPreflightBlocker> blockers,
        List<String> visibilityLimits
) {

    public RuntimeConfigurationDeepPreflight {
        repositories = repositories != null ? List.copyOf(repositories) : List.of();
        blockers = blockers != null ? List.copyOf(blockers) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public boolean ready() {
        return status == RuntimeConfigurationDeepPreflightStatus.READY;
    }
}
