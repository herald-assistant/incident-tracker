package pl.mkn.tdw.features.configdriftviewer.deep.model;

import java.util.List;

public record ConfigDriftViewerDeepCoverage(
        int repositoriesPlanned,
        int repositoriesSearched,
        int keysSearched,
        int filesInspected,
        int codeMatches,
        List<String> unavailableRepositoryIds,
        List<String> systemsWithoutCodeSearchScope
) {

    public ConfigDriftViewerDeepCoverage {
        unavailableRepositoryIds = unavailableRepositoryIds != null
                ? List.copyOf(unavailableRepositoryIds)
                : List.of();
        systemsWithoutCodeSearchScope = systemsWithoutCodeSearchScope != null
                ? List.copyOf(systemsWithoutCodeSearchScope)
                : List.of();
    }
}
