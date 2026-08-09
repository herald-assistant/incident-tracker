package pl.mkn.tdw.features.configdriftviewer.deep.model;

import java.util.List;

public record ConfigDriftViewerDeepRepositoryScope(
        String scopeId,
        String repositoryId,
        String role,
        Integer priority,
        String projectPath,
        String projectName,
        String searchMode,
        List<String> pathPrefixes,
        String requestedRef,
        String usedRef,
        ConfigDriftViewerCodeRefSource refSource,
        boolean refExists,
        boolean deploymentRefConfirmed,
        boolean ready,
        List<String> visibilityLimits
) {

    public ConfigDriftViewerDeepRepositoryScope {
        pathPrefixes = pathPrefixes != null ? List.copyOf(pathPrefixes) : List.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
