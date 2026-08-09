package pl.mkn.tdw.features.configdriftviewer.deep.model;

import java.util.List;

public record ConfigDriftViewerDeepPreflight(
        ConfigDriftViewerDeepPreflightStatus status,
        String repositoryId,
        String systemId,
        String systemLabel,
        String resolvedConfigurationDirectory,
        List<ConfigDriftViewerDeepRepositoryScope> repositories,
        List<ConfigDriftViewerDeepPreflightBlocker> blockers,
        List<String> visibilityLimits
) {

    public ConfigDriftViewerDeepPreflight {
        repositories = repositories != null ? List.copyOf(repositories) : List.of();
        blockers = blockers != null ? List.copyOf(blockers) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }

    public boolean ready() {
        return status == ConfigDriftViewerDeepPreflightStatus.READY;
    }
}
